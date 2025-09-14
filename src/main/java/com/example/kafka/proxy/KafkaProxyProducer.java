package com.example.kafka.proxy;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.errors.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

/**
 * Proxy wrapper for Kafka Producer that integrates with metadata service and supports interceptors.
 * 
 * @param <K> the key type
 * @param <V> the value type
 */
public class KafkaProxyProducer<K, V> implements Producer<K, V> {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaProxyProducer.class);
    
    private final Producer<K, V> delegate;
    private final KafkaMetadataService metadataService;
    private final List<ProducerInterceptor<K, V>> interceptors;
    private final String topicName;
    
    public KafkaProxyProducer(String topicName, 
                             KafkaMetadataService metadataService,
                             Properties baseProperties) 
            throws KafkaMetadataService.MetadataServiceException {
        this.topicName = topicName;
        this.metadataService = metadataService;
        this.interceptors = new CopyOnWriteArrayList<>();
        
        // Get topic metadata and configure producer
        TopicMetadata metadata = metadataService.getTopicMetadata(topicName)
            .orElseThrow(() -> new KafkaMetadataService.MetadataServiceException(
                "Topic metadata not found for: " + topicName));
        
        Properties producerProps = buildProducerProperties(baseProperties, metadata);
        this.delegate = new KafkaProducer<>(producerProps);
        
        logger.info("Created Kafka proxy producer for topic: {}", topicName);
    }
    
    /**
     * Registers a producer interceptor.
     * 
     * @param interceptor the interceptor to register
     */
    public void registerInterceptor(ProducerInterceptor<K, V> interceptor) {
        interceptors.add(interceptor);
        logger.debug("Registered producer interceptor: {}", interceptor.getClass().getSimpleName());
    }
    
    /**
     * Unregisters a producer interceptor.
     * 
     * @param interceptor the interceptor to unregister
     */
    public void unregisterInterceptor(ProducerInterceptor<K, V> interceptor) {
        interceptors.remove(interceptor);
        logger.debug("Unregistered producer interceptor: {}", interceptor.getClass().getSimpleName());
    }
    
    @Override
    public Future<RecordMetadata> send(ProducerRecord<K, V> record) {
        return send(record, null);
    }
    
    @Override
    public Future<RecordMetadata> send(ProducerRecord<K, V> record, Callback callback) {
        // Apply interceptors
        ProducerRecord<K, V> processedRecord = record;
        for (ProducerInterceptor<K, V> interceptor : interceptors) {
            try {
                processedRecord = interceptor.onSend(processedRecord);
            } catch (Exception e) {
                logger.warn("Error in producer interceptor onSend", e);
            }
        }
        
        // Create callback wrapper to notify interceptors
        Callback wrappedCallback = (metadata, exception) -> {
            // Notify interceptors
            for (ProducerInterceptor<K, V> interceptor : interceptors) {
                try {
                    interceptor.onAcknowledgement(metadata, exception);
                } catch (Exception e) {
                    logger.warn("Error in producer interceptor onAcknowledgement", e);
                }
            }
            
            // Call original callback
            if (callback != null) {
                callback.onCompletion(metadata, exception);
            }
        };
        
        return delegate.send(processedRecord, wrappedCallback);
    }
    
    @Override
    public void flush() {
        delegate.flush();
    }
    
    @Override
    public List<PartitionInfo> partitionsFor(String topic) {
        return delegate.partitionsFor(topic);
    }
    
    @Override
    public Map<MetricName, ? extends Metric> metrics() {
        return delegate.metrics();
    }
    
    @Override
    public void close() {
        close(Duration.ofSeconds(30));
    }
    
    @Override
    public void close(Duration timeout) {
        logger.info("Closing Kafka proxy producer for topic: {}", topicName);
        
        // Close interceptors
        for (ProducerInterceptor<K, V> interceptor : interceptors) {
            try {
                interceptor.close();
            } catch (Exception e) {
                logger.warn("Error closing producer interceptor", e);
            }
        }
        
        delegate.close(timeout);
    }
    
    @Override
    public void initTransactions() throws ProducerFencedException {
        delegate.initTransactions();
    }
    
    @Override
    public void beginTransaction() throws ProducerFencedException {
        delegate.beginTransaction();
    }
    
    @Override
    public void sendOffsetsToTransaction(Map offsets, String consumerGroupId) 
            throws ProducerFencedException {
        delegate.sendOffsetsToTransaction(offsets, consumerGroupId);
    }
    
    @Override
    public void sendOffsetsToTransaction(Map<org.apache.kafka.common.TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsets, 
                                       org.apache.kafka.clients.consumer.ConsumerGroupMetadata groupMetadata) 
            throws ProducerFencedException {
        delegate.sendOffsetsToTransaction(offsets, groupMetadata);
    }
    
    @Override
    public void commitTransaction() throws ProducerFencedException {
        delegate.commitTransaction();
    }
    
    @Override
    public void abortTransaction() throws ProducerFencedException {
        delegate.abortTransaction();
    }
    
    @Override
    public org.apache.kafka.common.Uuid clientInstanceId(Duration timeout) {
        return delegate.clientInstanceId(timeout);
    }
    
    private Properties buildProducerProperties(Properties baseProperties, TopicMetadata metadata) {
        Properties props = new Properties(baseProperties);
        
        // Build bootstrap servers from broker info
        StringBuilder bootstrapServers = new StringBuilder();
        for (int i = 0; i < metadata.brokers().size(); i++) {
            if (i > 0) bootstrapServers.append(",");
            TopicMetadata.BrokerInfo broker = metadata.brokers().get(i);
            bootstrapServers.append(broker.host()).append(":").append(broker.port());
        }
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers.toString());
        
        // Configure security
        TopicMetadata.SecurityConfig secConfig = metadata.securityConfig();
        props.setProperty("security.protocol", secConfig.protocol().name());
        
        if (secConfig.keystorePath() != null) {
            props.setProperty("ssl.keystore.location", secConfig.keystorePath());
            props.setProperty("ssl.keystore.password", secConfig.keystorePassword());
        }
        
        if (secConfig.truststorePath() != null) {
            props.setProperty("ssl.truststore.location", secConfig.truststorePath());
            props.setProperty("ssl.truststore.password", secConfig.truststorePassword());
        }
        
        if (secConfig.saslMechanism() != null) {
            props.setProperty("sasl.mechanism", secConfig.saslMechanism());
        }
        
        // Add any additional security properties
        props.putAll(secConfig.additionalSecurityProps());
        
        return props;
    }
}
