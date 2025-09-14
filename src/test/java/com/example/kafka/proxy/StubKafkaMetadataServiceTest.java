package com.example.kafka.proxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StubKafkaMetadataServiceTest {
    
    private StubKafkaMetadataService service;
    
    @BeforeEach
    void setUp() {
        service = new StubKafkaMetadataService();
    }
    
    @Test
    void testGetExistingTopicMetadata() throws KafkaMetadataService.MetadataServiceException {
        Optional<TopicMetadata> metadata = service.getTopicMetadata("sample-topic");
        
        assertTrue(metadata.isPresent());
        assertEquals("sample-topic", metadata.get().topicName());
        assertEquals(3, metadata.get().brokers().size());
        assertEquals(TopicMetadata.SecurityConfig.SecurityProtocol.PLAINTEXT, 
            metadata.get().securityConfig().protocol());
    }
    
    @Test
    void testGetNonExistentTopicMetadata() throws KafkaMetadataService.MetadataServiceException {
        Optional<TopicMetadata> metadata = service.getTopicMetadata("non-existent-topic");
        assertFalse(metadata.isPresent());
    }
    
    @Test
    void testIsHealthy() {
        assertTrue(service.isHealthy());
        
        service.setHealthy(false);
        assertFalse(service.isHealthy());
        
        service.setHealthy(true);
        assertTrue(service.isHealthy());
    }
    
    @Test
    void testUnhealthyServiceThrowsException() {
        service.setHealthy(false);
        
        assertThrows(KafkaMetadataService.MetadataServiceException.class, 
            () -> service.getTopicMetadata("sample-topic"));
        
        assertThrows(KafkaMetadataService.MetadataServiceException.class, 
            () -> service.refreshTopicMetadata("sample-topic"));
    }
    
    @Test
    void testAddTopicMetadata() throws KafkaMetadataService.MetadataServiceException {
        TopicMetadata newTopic = new TopicMetadata(
            "new-topic",
            List.of(new TopicMetadata.BrokerInfo(1, "localhost", 9092, true)),
            TopicMetadata.SecurityConfig.plaintext(),
            Map.of()
        );
        
        service.addTopicMetadata(newTopic);
        
        Optional<TopicMetadata> retrieved = service.getTopicMetadata("new-topic");
        assertTrue(retrieved.isPresent());
        assertEquals("new-topic", retrieved.get().topicName());
    }
    
    @Test
    void testRemoveTopicMetadata() throws KafkaMetadataService.MetadataServiceException {
        assertTrue(service.getTopicMetadata("sample-topic").isPresent());
        
        service.removeTopicMetadata("sample-topic");
        
        assertFalse(service.getTopicMetadata("sample-topic").isPresent());
    }
    
    @Test
    void testRefreshTopicMetadata() {
        assertDoesNotThrow(() -> service.refreshTopicMetadata("sample-topic"));
    }
    
    @Test
    void testGetCachedTopicNames() {
        var topicNames = service.getCachedTopicNames();
        assertTrue(topicNames.contains("sample-topic"));
        assertTrue(topicNames.contains("secure-topic"));
        assertEquals(2, topicNames.size());
    }
}
