package com.example.kafka.proxy;

import java.util.Optional;

/**
 * Service interface for retrieving Kafka topic metadata including broker information
 * and security configuration.
 */
public interface KafkaMetadataService {
    
    /**
     * Retrieves metadata for the specified topic.
     * 
     * @param topicName the name of the topic
     * @return topic metadata if found, empty otherwise
     * @throws MetadataServiceException if there's an error retrieving metadata
     */
    Optional<TopicMetadata> getTopicMetadata(String topicName) throws MetadataServiceException;
    
    /**
     * Checks if the metadata service is healthy and available.
     * 
     * @return true if the service is healthy, false otherwise
     */
    boolean isHealthy();
    
    /**
     * Refreshes cached metadata for a specific topic.
     * 
     * @param topicName the name of the topic to refresh
     * @throws MetadataServiceException if there's an error refreshing metadata
     */
    void refreshTopicMetadata(String topicName) throws MetadataServiceException;
    
    /**
     * Exception thrown when there are issues with the metadata service.
     */
    class MetadataServiceException extends Exception {
        public MetadataServiceException(String message) {
            super(message);
        }
        
        public MetadataServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
