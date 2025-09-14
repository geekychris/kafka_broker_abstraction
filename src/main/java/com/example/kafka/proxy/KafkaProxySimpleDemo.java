package com.example.kafka.proxy;

import com.example.kafka.proxy.interceptors.LoggingProducerInterceptor;
import com.example.kafka.proxy.interceptors.MessageAugmentationProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Simple demonstration of Kafka proxy configuration and interceptor setup.
 * This demo shows the proxy's ability to configure connection properties based on metadata
 * and register interceptors, without actually connecting to Kafka brokers.
 */
public class KafkaProxySimpleDemo {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaProxySimpleDemo.class);
    
    public static void main(String[] args) {
        logger.info("=== Kafka Proxy Simple Demo ===");
        
        try {
            // Create and configure metadata service
            StubKafkaMetadataService metadataService = new StubKafkaMetadataService();
            
            // Show metadata service capabilities
            demonstrateMetadataService(metadataService);
            
            // Show interceptor functionality
            demonstrateInterceptors();
            
            // Show proxy configuration (without actual connection)
            demonstrateProxyConfiguration(metadataService);
            
        } catch (Exception e) {
            logger.error("Demo failed", e);
        }
    }
    
    private static void demonstrateMetadataService(StubKafkaMetadataService metadataService) {
        logger.info("--- Metadata Service Demo ---");
        
        try {
            // Show available topics
            var topicNames = metadataService.getCachedTopicNames();
            logger.info("Available topics: {}", topicNames);
            
            // Get metadata for sample topic
            var metadata = metadataService.getTopicMetadata("sample-topic");
            if (metadata.isPresent()) {
                TopicMetadata topic = metadata.get();
                logger.info("Sample topic brokers: {}", topic.brokers().size());
                logger.info("Security protocol: {}", topic.securityConfig().protocol());
            }
            
            // Show health check
            logger.info("Metadata service healthy: {}", metadataService.isHealthy());
            
        } catch (Exception e) {
            logger.error("Metadata service demo failed", e);
        }
    }
    
    private static void demonstrateInterceptors() {
        logger.info("--- Interceptor Demo ---");
        
        // Create a sample producer record
        ProducerRecord<String, String> record = new ProducerRecord<>(
            "demo-topic", "test-key", "Hello, interceptors!");
        
        // Test logging interceptor
        LoggingProducerInterceptor<String, String> loggingInterceptor = 
            new LoggingProducerInterceptor<>("demo-logger", true);
        
        logger.info("Testing logging interceptor:");
        ProducerRecord<String, String> processedRecord = loggingInterceptor.onSend(record);
        loggingInterceptor.onAcknowledgement(null, null); // Simulate successful send
        
        // Test message augmentation interceptor
        MessageAugmentationProducerInterceptor<String, String> augmentationInterceptor = 
            new MessageAugmentationProducerInterceptor<>(
                value -> "[DEMO-PROCESSED] " + value, true, true, "demo-client"
            );
        
        logger.info("Testing message augmentation interceptor:");
        ProducerRecord<String, String> augmentedRecord = augmentationInterceptor.onSend(processedRecord);
        
        logger.info("Original value: {}", record.value());
        logger.info("Augmented value: {}", augmentedRecord.value());
        logger.info("Added headers count: {}", augmentedRecord.headers().toArray().length);
    }
    
    private static void demonstrateProxyConfiguration(KafkaMetadataService metadataService) {
        logger.info("--- Proxy Configuration Demo ---");
        
        try {
            // Show how proxy would configure producer properties
            Properties baseProps = new Properties();
            baseProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            baseProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            baseProps.put(ProducerConfig.CLIENT_ID_CONFIG, "demo-producer");
            
            logger.info("Base properties: {}", baseProps.size());
            
            // Get topic metadata to show how proxy would enhance configuration
            var metadata = metadataService.getTopicMetadata("sample-topic");
            if (metadata.isPresent()) {
                TopicMetadata topic = metadata.get();
                
                // Simulate what proxy would do for broker configuration
                StringBuilder brokers = new StringBuilder();
                for (int i = 0; i < topic.brokers().size(); i++) {
                    if (i > 0) brokers.append(",");
                    var broker = topic.brokers().get(i);
                    brokers.append(broker.host()).append(":").append(broker.port());
                }
                
                logger.info("Proxy would set bootstrap.servers to: {}", brokers.toString());
                logger.info("Proxy would set security.protocol to: {}", topic.securityConfig().protocol());
                logger.info("Topic has {} additional properties", topic.additionalProperties().size());
            }
            
        } catch (Exception e) {
            logger.error("Configuration demo failed", e);
        }
    }
}
