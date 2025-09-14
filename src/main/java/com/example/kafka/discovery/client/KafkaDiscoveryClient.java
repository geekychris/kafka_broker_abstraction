package com.example.kafka.discovery.client;

import com.example.kafka.discovery.common.KafkaMetadataService;
import com.example.kafka.discovery.common.TopicMetadata;
import com.example.kafka.discovery.common.interceptors.LoggingConsumerInterceptor;
import com.example.kafka.discovery.common.interceptors.LoggingProducerInterceptor;
import com.example.kafka.discovery.common.interceptors.MessageAugmentationProducerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;

/**
 * Main client library for Kafka broker discovery integration.
 * 
 * This client looks up broker connection details and security configuration from a discovery service,
 * then creates standard Kafka producers and consumers that communicate directly with Kafka brokers.
 * It does NOT act as a proxy - all Kafka traffic flows directly between clients and brokers.
 */
public class KafkaDiscoveryClient {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaDiscoveryClient.class);
    
    private final KafkaMetadataService metadataService;
    private final boolean debugEnabled;
    
    /**
     * Creates a new discovery client with the specified discovery service URL.
     * 
     * @param discoveryServiceUrl URL of the broker discovery service
     */
    public KafkaDiscoveryClient(String discoveryServiceUrl) {
        this(discoveryServiceUrl, false);
    }
    
    /**
     * Creates a new discovery client with the specified discovery service URL and debug option.
     * 
     * @param discoveryServiceUrl URL of the broker discovery service
     * @param debugEnabled whether to enable debug logging and interceptors
     */
    public KafkaDiscoveryClient(String discoveryServiceUrl, boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        
        RestClientConfig config = RestClientConfig.builder()
            .baseUrl(discoveryServiceUrl)
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(10))
            .maxRetries(2)
            .retryDelay(Duration.ofMillis(500))
            .build();
        
        this.metadataService = new RestKafkaMetadataService(config);
        
        logger.info("Created Kafka Discovery Client for service: {}", discoveryServiceUrl);
    }
    
    /**
     * Creates a Kafka producer that will automatically discover broker and security configuration.
     * 
     * @param topicName the topic this producer will send to
     * @param <K> key type (defaults to String serialization)
     * @param <V> value type (defaults to String serialization)
     * @return configured Kafka discovery producer
     * @throws KafkaMetadataService.MetadataServiceException if topic metadata cannot be retrieved
     */
    public <K, V> KafkaDiscoveryProducer<K, V> createProducer(String topicName) 
            throws KafkaMetadataService.MetadataServiceException {
        return createProducer(topicName, createDefaultProducerProperties());
    }
    
    /**
     * Creates a Kafka producer with custom base properties.
     * 
     * @param topicName the topic this producer will send to
     * @param baseProperties base producer configuration (bootstrap.servers will be overridden)
     * @param <K> key type
     * @param <V> value type
     * @return configured Kafka discovery producer
     * @throws KafkaMetadataService.MetadataServiceException if topic metadata cannot be retrieved
     */
    public <K, V> KafkaDiscoveryProducer<K, V> createProducer(String topicName, Properties baseProperties) 
            throws KafkaMetadataService.MetadataServiceException {
        
        KafkaDiscoveryProducer<K, V> producer = new KafkaDiscoveryProducer<>(topicName, metadataService, baseProperties);
        
        // Add debug interceptors if enabled
        if (debugEnabled) {
            producer.registerInterceptor(new LoggingProducerInterceptor<>("discovery-client", false));
            producer.registerInterceptor(new MessageAugmentationProducerInterceptor<>());
        }
        
        logger.info("Created producer for topic: {} (debug: {})", topicName, debugEnabled);
        return producer;
    }
    
    /**
     * Creates a Kafka consumer that will automatically discover broker and security configuration.
     * 
     * @param topicName the topic this consumer will read from
     * @param consumerGroup the consumer group ID
     * @param <K> key type (defaults to String deserialization)
     * @param <V> value type (defaults to String deserialization)
     * @return configured Kafka discovery consumer
     * @throws KafkaMetadataService.MetadataServiceException if topic metadata cannot be retrieved
     */
    public <K, V> KafkaDiscoveryConsumer<K, V> createConsumer(String topicName, String consumerGroup) 
            throws KafkaMetadataService.MetadataServiceException {
        return createConsumer(topicName, createDefaultConsumerProperties(consumerGroup));
    }
    
    /**
     * Creates a Kafka consumer with custom base properties.
     * 
     * @param topicName the topic this consumer will read from
     * @param baseProperties base consumer configuration (bootstrap.servers will be overridden)
     * @param <K> key type
     * @param <V> value type
     * @return configured Kafka discovery consumer
     * @throws KafkaMetadataService.MetadataServiceException if topic metadata cannot be retrieved
     */
    public <K, V> KafkaDiscoveryConsumer<K, V> createConsumer(String topicName, Properties baseProperties) 
            throws KafkaMetadataService.MetadataServiceException {
        
        KafkaDiscoveryConsumer<K, V> consumer = new KafkaDiscoveryConsumer<>(topicName, metadataService, baseProperties);
        
        // Add debug interceptors if enabled
        if (debugEnabled) {
            consumer.registerInterceptor(new LoggingConsumerInterceptor<>("discovery-client", false));
        }
        
        logger.info("Created consumer for topic: {} (debug: {})", topicName, debugEnabled);
        return consumer;
    }
    
    /**
     * Gets topic metadata for the specified topic.
     * 
     * @param topicName the topic name
     * @return topic metadata if found
     * @throws KafkaMetadataService.MetadataServiceException if metadata cannot be retrieved
     */
    public TopicMetadata getTopicMetadata(String topicName) throws KafkaMetadataService.MetadataServiceException {
        return metadataService.getTopicMetadata(topicName)
            .orElseThrow(() -> new KafkaMetadataService.MetadataServiceException(
                "Topic metadata not found for: " + topicName));
    }
    
    /**
     * Checks if the discovery service is healthy.
     * 
     * @return true if the service is healthy
     */
    public boolean isServiceHealthy() {
        return metadataService.isHealthy();
    }
    
    /**
     * Refreshes cached metadata for a topic.
     * 
     * @param topicName the topic name
     * @throws KafkaMetadataService.MetadataServiceException if refresh fails
     */
    public void refreshTopicMetadata(String topicName) throws KafkaMetadataService.MetadataServiceException {
        metadataService.refreshTopicMetadata(topicName);
    }
    
    /**
     * Closes the client and releases resources.
     */
    public void close() {
        if (metadataService instanceof RestKafkaMetadataService) {
            ((RestKafkaMetadataService) metadataService).close();
        }
        logger.info("Closed Kafka Discovery Client");
    }
    
    private Properties createDefaultProducerProperties() {
        Properties props = new Properties();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "kafka-discovery-producer");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        return props;
    }
    
    private Properties createDefaultConsumerProperties(String consumerGroup) {
        Properties props = new Properties();
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "kafka-discovery-consumer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");
        return props;
    }
}
