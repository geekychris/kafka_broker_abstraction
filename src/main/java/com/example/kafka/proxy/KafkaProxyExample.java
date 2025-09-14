package com.example.kafka.proxy;

import com.example.kafka.proxy.interceptors.LoggingProducerInterceptor;
import com.example.kafka.proxy.interceptors.LoggingConsumerInterceptor;
import com.example.kafka.proxy.interceptors.MessageAugmentationProducerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * Example demonstrating how to use the Kafka proxy client.
 */
public class KafkaProxyExample {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaProxyExample.class);
    
    public static void main(String[] args) {
        // Create metadata service
        StubKafkaMetadataService metadataService = new StubKafkaMetadataService();
        
        // Demonstrate producer usage
        demonstrateProducer(metadataService);
        
        // Demonstrate consumer usage
        demonstrateConsumer(metadataService);
    }
    
    private static void demonstrateProducer(KafkaMetadataService metadataService) {
        logger.info("=== Demonstrating Kafka Proxy Producer ===");
        
        try {
            // Create base producer properties
            Properties producerProps = new Properties();
            producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProps.put(ProducerConfig.CLIENT_ID_CONFIG, "kafka-proxy-example-producer");
            producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
            producerProps.put(ProducerConfig.RETRIES_CONFIG, 3);
            
            // Create proxy producer
            KafkaProxyProducer<String, String> producer = new KafkaProxyProducer<>(
                "sample-topic", metadataService, producerProps);
            
            // Register interceptors
            producer.registerInterceptor(new LoggingProducerInterceptor<>("example-producer", false));
            producer.registerInterceptor(new MessageAugmentationProducerInterceptor<>(
                value -> "[AUGMENTED] " + value, true, true, "example-client"
            ));
            
            // Send some messages
            for (int i = 0; i < 5; i++) {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                    "sample-topic", 
                    "key-" + i, 
                    "Hello, Kafka Proxy! Message " + i
                );
                
                try {
                    producer.send(record).get(); // Wait for acknowledgment
                    logger.info("Successfully sent message {}", i);
                } catch (ExecutionException e) {
                    logger.error("Failed to send message {}: {}", i, e.getCause().getMessage());
                }
            }
            
            producer.flush();
            producer.close();
            
        } catch (Exception e) {
            logger.error("Error in producer demonstration", e);
        }
    }
    
    private static void demonstrateConsumer(KafkaMetadataService metadataService) {
        logger.info("=== Demonstrating Kafka Proxy Consumer ===");
        
        try {
            // Create base consumer properties
            Properties consumerProps = new Properties();
            consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "example-group");
            consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
            
            // Create proxy consumer
            KafkaProxyConsumer<String, String> consumer = new KafkaProxyConsumer<>(
                "sample-topic", metadataService, consumerProps);
            
            // Register interceptors
            consumer.registerInterceptor(new LoggingConsumerInterceptor<>("example-consumer", false));
            
            // Subscribe to topic
            consumer.subscribe(Collections.singletonList("sample-topic"));
            
            // Poll for messages (in a real application, this would be in a loop)
            logger.info("Polling for messages...");
            for (int i = 0; i < 3; i++) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                if (!records.isEmpty()) {
                    logger.info("Received {} records in poll {}", records.count(), i);
                } else {
                    logger.info("No records received in poll {}", i);
                }
            }
            
            consumer.close();
            
        } catch (Exception e) {
            logger.error("Error in consumer demonstration", e);
        }
    }
}
