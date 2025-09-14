package com.example.kafka.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stub implementation of KafkaMetadataService for testing and development.
 */
public class StubKafkaMetadataService implements KafkaMetadataService {
    
    private static final Logger logger = LoggerFactory.getLogger(StubKafkaMetadataService.class);
    
    private final Map<String, TopicMetadata> topicMetadataCache = new ConcurrentHashMap<>();
    private volatile boolean healthy = true;
    
    public StubKafkaMetadataService() {
        // Pre-populate with some sample topic metadata
        initializeDefaultTopics();
    }
    
    private void initializeDefaultTopics() {
        // Sample topic with PLAINTEXT security
        TopicMetadata sampleTopic = new TopicMetadata(
            "sample-topic",
            List.of(
                new TopicMetadata.BrokerInfo(1, "localhost", 9092, true),
                new TopicMetadata.BrokerInfo(2, "localhost", 9093, false),
                new TopicMetadata.BrokerInfo(3, "localhost", 9094, false)
            ),
            TopicMetadata.SecurityConfig.plaintext(),
            Map.of("replication.factor", "3", "min.insync.replicas", "2")
        );
        topicMetadataCache.put("sample-topic", sampleTopic);
        
        // Sample topic with SSL security
        TopicMetadata secureTopic = new TopicMetadata(
            "secure-topic",
            List.of(
                new TopicMetadata.BrokerInfo(10, "secure-kafka1.example.com", 9092, true),
                new TopicMetadata.BrokerInfo(11, "secure-kafka2.example.com", 9092, false)
            ),
            TopicMetadata.SecurityConfig.ssl(
                "/path/to/client.keystore.jks", "keystorepass",
                "/path/to/client.truststore.jks", "truststorepass"
            ),
            Map.of("replication.factor", "2", "min.insync.replicas", "1")
        );
        topicMetadataCache.put("secure-topic", secureTopic);
        
        logger.info("Initialized stub metadata service with {} topics", topicMetadataCache.size());
    }
    
    @Override
    public Optional<TopicMetadata> getTopicMetadata(String topicName) throws MetadataServiceException {
        logger.debug("Retrieving metadata for topic: {}", topicName);
        
        if (!healthy) {
            throw new MetadataServiceException("Metadata service is not healthy");
        }
        
        // Simulate network delay
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MetadataServiceException("Interrupted while retrieving metadata", e);
        }
        
        TopicMetadata metadata = topicMetadataCache.get(topicName);
        if (metadata != null) {
            logger.debug("Found metadata for topic: {}", topicName);
        } else {
            logger.warn("No metadata found for topic: {}", topicName);
        }
        
        return Optional.ofNullable(metadata);
    }
    
    @Override
    public boolean isHealthy() {
        return healthy;
    }
    
    @Override
    public void refreshTopicMetadata(String topicName) throws MetadataServiceException {
        logger.info("Refreshing metadata for topic: {}", topicName);
        
        if (!healthy) {
            throw new MetadataServiceException("Metadata service is not healthy");
        }
        
        // Simulate refresh operation
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MetadataServiceException("Interrupted while refreshing metadata", e);
        }
        
        logger.info("Successfully refreshed metadata for topic: {}", topicName);
    }
    
    /**
     * Adds or updates topic metadata in the stub service.
     * 
     * @param metadata the topic metadata to add/update
     */
    public void addTopicMetadata(TopicMetadata metadata) {
        topicMetadataCache.put(metadata.topicName(), metadata);
        logger.info("Added metadata for topic: {}", metadata.topicName());
    }
    
    /**
     * Removes topic metadata from the stub service.
     * 
     * @param topicName the name of the topic to remove
     */
    public void removeTopicMetadata(String topicName) {
        topicMetadataCache.remove(topicName);
        logger.info("Removed metadata for topic: {}", topicName);
    }
    
    /**
     * Sets the health status of the service.
     * 
     * @param healthy true if the service should be healthy, false otherwise
     */
    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
        logger.info("Set metadata service health status to: {}", healthy);
    }
    
    /**
     * Returns all cached topic names.
     * 
     * @return set of topic names
     */
    public java.util.Set<String> getCachedTopicNames() {
        return java.util.Set.copyOf(topicMetadataCache.keySet());
    }
}
