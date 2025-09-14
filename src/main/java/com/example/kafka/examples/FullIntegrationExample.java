package com.example.kafka.examples;

import com.example.kafka.discovery.client.KafkaDiscoveryClient;
import com.example.kafka.discovery.client.KafkaDiscoveryConsumer;
import com.example.kafka.discovery.client.KafkaDiscoveryProducer;
import com.example.kafka.discovery.server.BrokerDiscoveryService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.Future;

/**
 * Comprehensive example demonstrating the complete Kafka broker discovery integration.
 * 
 * This example shows:
 * 1. Starting the discovery service
 * 2. Creating a discovery client
 * 3. Using the client to create Kafka producers/consumers with automatic broker discovery
 * 4. Producing and consuming messages (with local Kafka if available)
 * 5. Demonstrating interceptors and debugging features
 */
public class FullIntegrationExample {
    
    private static final Logger logger = LoggerFactory.getLogger(FullIntegrationExample.class);
    private static final int DISCOVERY_SERVICE_PORT = 8080;
    private static final String DISCOVERY_SERVICE_URL = "http://localhost:" + DISCOVERY_SERVICE_PORT;
    private static final String TOPIC_NAME = "dev-topic";
    
    public static void main(String[] args) {
        BrokerDiscoveryService discoveryService = null;
        KafkaDiscoveryClient discoveryClient = null;
        
        try {
            // =================================
            // STEP 1: Start Discovery Service
            // =================================
            logger.info("=== Starting Broker Discovery Service ===");
            discoveryService = new BrokerDiscoveryService(DISCOVERY_SERVICE_PORT);
            discoveryService.start();
            
            // Wait for service to fully start
            Thread.sleep(1000);
            
            // =================================
            // STEP 2: Create Discovery Client
            // =================================
            logger.info("=== Creating Discovery Client ===");
            discoveryClient = new KafkaDiscoveryClient(DISCOVERY_SERVICE_URL, true); // Enable debug mode
            
            // Verify client can connect to discovery service
            if (!discoveryClient.isServiceHealthy()) {
                throw new RuntimeException("Discovery service is not healthy!");
            }
            logger.info("✓ Discovery service is healthy");
            
            // =================================
            // STEP 3: Demonstrate Metadata Retrieval
            // =================================
            logger.info("=== Demonstrating Metadata Retrieval ===");
            var topicMetadata = discoveryClient.getTopicMetadata(TOPIC_NAME);
            logger.info("Retrieved metadata for topic: {}", TOPIC_NAME);
            logger.info("  Brokers: {}", topicMetadata.brokers().size());
            logger.info("  Security Protocol: {}", topicMetadata.securityConfig().protocol());
            
            topicMetadata.brokers().forEach(broker -> 
                logger.info("  Broker {} at {}:{} (controller: {})", 
                    broker.id(), broker.host(), broker.port(), broker.isController()));
            
            // =================================
            // STEP 4: Create Kafka Producer with Discovery
            // =================================
            logger.info("=== Creating Kafka Producer with Discovery ===");
            KafkaDiscoveryProducer<String, String> producer = null;
            
            try {
                producer = discoveryClient.createProducer(TOPIC_NAME);
                logger.info("✓ Successfully created producer for topic: {}", TOPIC_NAME);
                
                // Demonstrate sending messages (will fail if no Kafka brokers are running, but that's okay)
                demonstrateProducer(producer, TOPIC_NAME);
                
            } catch (Exception e) {
                logger.info("Note: Producer creation failed (expected if Kafka is not running): {}", e.getMessage());
            }
            
            // =================================
            // STEP 5: Create Kafka Consumer with Discovery
            // =================================
            logger.info("=== Creating Kafka Consumer with Discovery ===");
            KafkaDiscoveryConsumer<String, String> consumer = null;
            
            try {
                consumer = discoveryClient.createConsumer(TOPIC_NAME, "example-group");
                logger.info("✓ Successfully created consumer for topic: {}", TOPIC_NAME);
                
                // Demonstrate consuming messages (will timeout if no Kafka brokers are running)
                demonstrateConsumer(consumer, TOPIC_NAME);
                
            } catch (Exception e) {
                logger.info("Note: Consumer creation failed (expected if Kafka is not running): {}", e.getMessage());
            }
            
            // =================================
            // STEP 6: Demonstrate Service Management Features
            // =================================
            logger.info("=== Demonstrating Service Management ===");
            
            // Refresh topic metadata
            discoveryClient.refreshTopicMetadata(TOPIC_NAME);
            logger.info("✓ Refreshed metadata for topic: {}", TOPIC_NAME);
            
            // Try to get metadata for a non-existent topic
            try {
                discoveryClient.getTopicMetadata("non-existent-topic");
            } catch (Exception e) {
                logger.info("✓ Correctly handled non-existent topic: {}", e.getMessage());
            }
            
            // =================================
            // STEP 7: Cleanup
            // =================================
            logger.info("=== Cleaning Up Resources ===");
            if (producer != null) {
                producer.close();
                logger.info("✓ Closed producer");
            }
            if (consumer != null) {
                consumer.close();
                logger.info("✓ Closed consumer");
            }
            
            discoveryClient.close();
            logger.info("✓ Closed discovery client");
            
            logger.info("=== Example Completed Successfully ===");
            logger.info("");
            logger.info("This example demonstrated:");
            logger.info("✓ Starting a broker discovery service");
            logger.info("✓ Connecting a client to the discovery service");
            logger.info("✓ Automatic broker and security configuration retrieval");
            logger.info("✓ Creating Kafka producers/consumers with discovered configuration");
            logger.info("✓ Interceptors for debugging and message augmentation");
            logger.info("✓ Service health checking and metadata refresh");
            logger.info("");
            logger.info("To test with real Kafka:");
            logger.info("1. Start a local Kafka instance on localhost:9092");
            logger.info("2. Create the topic 'dev-topic'");
            logger.info("3. Run this example again");
            
        } catch (Exception e) {
            logger.error("Example failed", e);
        } finally {
            // Always cleanup
            if (discoveryClient != null) {
                discoveryClient.close();
            }
            if (discoveryService != null) {
                discoveryService.stop();
            }
        }
    }
    
    private static void demonstrateProducer(KafkaDiscoveryProducer<String, String> producer, String topicName) {
        logger.info("--- Producer Demonstration ---");
        
        try {
            // Send a few test messages
            for (int i = 1; i <= 3; i++) {
                String key = "key-" + i;
                String value = "Hello from discovery client! Message " + i;
                
                ProducerRecord<String, String> record = new ProducerRecord<>(topicName, key, value);
                Future<RecordMetadata> future = producer.send(record);
                
                logger.info("Sent message {}: {} = {}", i, key, value);
                
                // Try to get result (will timeout if Kafka is not available)
                try {
                    RecordMetadata metadata = future.get();
                    logger.info("✓ Message {} acknowledged: partition={}, offset={}", 
                        i, metadata.partition(), metadata.offset());
                } catch (Exception e) {
                    logger.info("⚠ Message {} send failed (Kafka not available): {}", i, e.getMessage());
                }
            }
            
            // Flush producer to ensure all messages are sent
            producer.flush();
            logger.info("✓ Producer flushed");
            
        } catch (Exception e) {
            logger.info("Producer demonstration failed (expected if no Kafka): {}", e.getMessage());
        }
    }
    
    private static void demonstrateConsumer(KafkaDiscoveryConsumer<String, String> consumer, String topicName) {
        logger.info("--- Consumer Demonstration ---");
        
        try {
            // Subscribe to the topic
            consumer.subscribe(Collections.singletonList(topicName));
            logger.info("✓ Subscribed to topic: {}", topicName);
            
            // Poll for messages (with short timeout since Kafka might not be available)
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
            
            if (records.isEmpty()) {
                logger.info("⚠ No messages received (Kafka might not be running or no messages available)");
            } else {
                logger.info("✓ Received {} messages", records.count());
                for (ConsumerRecord<String, String> record : records) {
                    logger.info("  Consumed: key={}, value={}, partition={}, offset={}", 
                        record.key(), record.value(), record.partition(), record.offset());
                }
            }
            
        } catch (Exception e) {
            logger.info("Consumer demonstration failed (expected if no Kafka): {}", e.getMessage());
        }
    }
}
