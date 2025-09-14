package com.example.kafka.proxy;

import com.example.kafka.discovery.common.KafkaMetadataService;
import com.example.kafka.discovery.common.TopicMetadata;
import com.example.kafka.discovery.client.RestClientConfig;
import com.example.kafka.discovery.client.RestKafkaMetadataService;
import com.example.kafka.discovery.server.BrokerDiscoveryService;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the broker discovery service and REST metadata client.
 */
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
public class BrokerDiscoveryIntegrationTest {
    
    private static BrokerDiscoveryService discoveryService;
    private static RestKafkaMetadataService restMetadataService;
    private static final int TEST_PORT = 9876;
    
    @BeforeAll
    static void startServices() throws InterruptedException {
        // Start the discovery service
        discoveryService = new BrokerDiscoveryService(TEST_PORT);
        discoveryService.start();
        
        // Wait longer for the service to fully start (server startup can take time)
        Thread.sleep(2000);
        
        // Create REST client configuration
        RestClientConfig config = RestClientConfig.builder()
            .baseUrl("http://localhost:" + TEST_PORT)
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(10))
            .build();
        
        // Create REST metadata service
        restMetadataService = new RestKafkaMetadataService(config);
    }
    
    @AfterAll
    static void stopServices() {
        if (restMetadataService != null) {
            restMetadataService.close();
        }
        if (discoveryService != null) {
            discoveryService.stop();
        }
    }
    
    @Test
    @org.junit.jupiter.api.Order(1) // Run this test first
    void testHealthCheck() {
        // Wait a bit more to ensure service is ready
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(restMetadataService.isHealthy(), "REST metadata service should be healthy");
    }
    
    @Test
    void testGetExistingTopic() throws KafkaMetadataService.MetadataServiceException {
        // The discovery service is initialized with some default topics
        Optional<TopicMetadata> metadata = restMetadataService.getTopicMetadata("dev-topic");
        
        assertTrue(metadata.isPresent(), "dev-topic should exist");
        
        TopicMetadata topic = metadata.get();
        assertEquals("dev-topic", topic.topicName());
        assertEquals(3, topic.brokers().size());
        assertEquals(TopicMetadata.SecurityConfig.SecurityProtocol.PLAINTEXT, 
            topic.securityConfig().protocol());
    }
    
    @Test
    void testGetNonExistentTopic() throws KafkaMetadataService.MetadataServiceException {
        Optional<TopicMetadata> metadata = restMetadataService.getTopicMetadata("non-existent-topic");
        assertFalse(metadata.isPresent(), "non-existent-topic should not exist");
    }
    
    @Test
    void testGetProdTopicWithSSL() throws KafkaMetadataService.MetadataServiceException {
        Optional<TopicMetadata> metadata = restMetadataService.getTopicMetadata("prod-topic");
        
        assertTrue(metadata.isPresent(), "prod-topic should exist");
        
        TopicMetadata topic = metadata.get();
        assertEquals("prod-topic", topic.topicName());
        assertEquals(3, topic.brokers().size());
        assertEquals(TopicMetadata.SecurityConfig.SecurityProtocol.SSL, 
            topic.securityConfig().protocol());
        
        // Verify SSL configuration
        assertNotNull(topic.securityConfig().keystorePath());
        assertNotNull(topic.securityConfig().truststorePath());
    }
    
    @Test
    void testGetStagingTopic() throws KafkaMetadataService.MetadataServiceException {
        Optional<TopicMetadata> metadata = restMetadataService.getTopicMetadata("staging-topic");
        
        assertTrue(metadata.isPresent(), "staging-topic should exist");
        
        TopicMetadata topic = metadata.get();
        assertEquals("staging-topic", topic.topicName());
        assertEquals(2, topic.brokers().size());
        assertEquals(TopicMetadata.SecurityConfig.SecurityProtocol.PLAINTEXT, 
            topic.securityConfig().protocol());
        
        // Check broker configuration
        List<TopicMetadata.BrokerInfo> brokers = topic.brokers();
        assertTrue(brokers.stream().anyMatch(b -> b.isController()), 
            "At least one broker should be a controller");
    }
    
    @Test
    void testRefreshTopicMetadata() throws KafkaMetadataService.MetadataServiceException {
        // This should not throw an exception for existing topics
        assertDoesNotThrow(() -> restMetadataService.refreshTopicMetadata("dev-topic"));
    }
    
    @Test
    void testRefreshNonExistentTopic() {
        // This should throw an exception for non-existent topics
        assertThrows(KafkaMetadataService.MetadataServiceException.class,
            () -> restMetadataService.refreshTopicMetadata("non-existent-topic"));
    }
    
    @Test
    void testServiceIsRunning() {
        assertTrue(discoveryService.isRunning(), "Discovery service should be running");
        assertEquals(TEST_PORT, discoveryService.getPort(), "Port should match configured port");
    }
    
    @Test
    @org.junit.jupiter.api.Order(100) // Run this test last
    void testUnhealthyService() {
        // Stop the service temporarily
        discoveryService.stop();
        
        // Wait a bit for shutdown to complete
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Health check should fail
        assertFalse(restMetadataService.isHealthy(), "Service should be unhealthy when stopped");
        
        // Restart for other tests
        discoveryService = new BrokerDiscoveryService(TEST_PORT);
        discoveryService.start();
        
        // Wait longer for restart
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
