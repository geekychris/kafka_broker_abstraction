package com.example.kafka.discovery.server;

import com.example.kafka.discovery.common.TopicMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for topic metadata used by the broker discovery service.
 */
public class TopicMetadataStore {
    
    private static final Logger logger = LoggerFactory.getLogger(TopicMetadataStore.class);
    
    private final Map<String, TopicMetadata> topicStore = new ConcurrentHashMap<>();
    
    public TopicMetadataStore() {
        // Initialize with some sample data
        initializeDefaultTopics();
    }
    
    /**
     * Retrieves metadata for a specific topic.
     * 
     * @param topicName the name of the topic
     * @return topic metadata if found, null otherwise
     */
    public TopicMetadata getTopicMetadata(String topicName) {
        TopicMetadata metadata = topicStore.get(topicName);
        if (metadata != null) {
            logger.debug("Retrieved metadata for topic: {}", topicName);
        } else {
            logger.debug("Topic metadata not found for: {}", topicName);
        }
        return metadata;
    }
    
    /**
     * Stores or updates metadata for a topic.
     * 
     * @param metadata the topic metadata to store
     */
    public void putTopicMetadata(TopicMetadata metadata) {
        topicStore.put(metadata.topicName(), metadata);
        logger.info("Stored metadata for topic: {}", metadata.topicName());
    }
    
    /**
     * Removes metadata for a topic.
     * 
     * @param topicName the name of the topic to remove
     * @return true if the topic was removed, false if it didn't exist
     */
    public boolean removeTopicMetadata(String topicName) {
        boolean removed = topicStore.remove(topicName) != null;
        if (removed) {
            logger.info("Removed metadata for topic: {}", topicName);
        } else {
            logger.debug("Attempted to remove non-existent topic: {}", topicName);
        }
        return removed;
    }
    
    /**
     * Lists all available topics.
     * 
     * @return set of all topic names
     */
    public Set<String> listTopics() {
        Set<String> topics = new HashSet<>(topicStore.keySet());
        logger.debug("Listed {} topics", topics.size());
        return topics;
    }
    
    /**
     * Gets all topic metadata.
     * 
     * @return map of topic name to metadata
     */
    public Map<String, TopicMetadata> getAllTopicMetadata() {
        Map<String, TopicMetadata> result = new HashMap<>(topicStore);
        logger.debug("Retrieved all metadata for {} topics", result.size());
        return result;
    }
    
    /**
     * Checks if a topic exists in the store.
     * 
     * @param topicName the name of the topic
     * @return true if the topic exists, false otherwise
     */
    public boolean containsTopic(String topicName) {
        return topicStore.containsKey(topicName);
    }
    
    /**
     * Clears all stored metadata.
     */
    public void clear() {
        int count = topicStore.size();
        topicStore.clear();
        logger.info("Cleared {} topics from metadata store", count);
    }
    
    /**
     * Returns the number of topics in the store.
     * 
     * @return number of topics
     */
    public int size() {
        return topicStore.size();
    }
    
    private void initializeDefaultTopics() {
        // Development topic with PLAINTEXT security
        TopicMetadata devTopic = new TopicMetadata(
            "dev-topic",
            List.of(
                new TopicMetadata.BrokerInfo(1, "localhost", 9092, true),
                new TopicMetadata.BrokerInfo(2, "localhost", 9093, false),
                new TopicMetadata.BrokerInfo(3, "localhost", 9094, false)
            ),
            TopicMetadata.SecurityConfig.plaintext(),
            Map.of("replication.factor", "3", "min.insync.replicas", "2")
        );
        putTopicMetadata(devTopic);
        
        // Production topic with SSL security
        TopicMetadata prodTopic = new TopicMetadata(
            "prod-topic",
            List.of(
                new TopicMetadata.BrokerInfo(10, "kafka-prod-1.example.com", 9092, true),
                new TopicMetadata.BrokerInfo(11, "kafka-prod-2.example.com", 9092, false),
                new TopicMetadata.BrokerInfo(12, "kafka-prod-3.example.com", 9092, false)
            ),
            TopicMetadata.SecurityConfig.ssl(
                "/etc/kafka/certs/client.keystore.jks", "keystorepass",
                "/etc/kafka/certs/client.truststore.jks", "truststorepass"
            ),
            Map.of("replication.factor", "3", "min.insync.replicas", "2", "cleanup.policy", "compact")
        );
        putTopicMetadata(prodTopic);
        
        // Staging topic with different broker setup
        TopicMetadata stagingTopic = new TopicMetadata(
            "staging-topic",
            List.of(
                new TopicMetadata.BrokerInfo(20, "kafka-staging.example.com", 9092, true),
                new TopicMetadata.BrokerInfo(21, "kafka-staging.example.com", 9093, false)
            ),
            TopicMetadata.SecurityConfig.plaintext(),
            Map.of("replication.factor", "2", "min.insync.replicas", "1")
        );
        putTopicMetadata(stagingTopic);
        
        logger.info("Initialized topic metadata store with {} default topics", topicStore.size());
    }
}
