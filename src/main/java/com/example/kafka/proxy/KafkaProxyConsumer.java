package com.example.kafka.proxy;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Proxy wrapper for Kafka Consumer that integrates with metadata service and supports interceptors.
 * 
 * @param <K> the key type
 * @param <V> the value type
 */
public class KafkaProxyConsumer<K, V> implements Consumer<K, V> {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaProxyConsumer.class);
    
    private final Consumer<K, V> delegate;
    private final KafkaMetadataService metadataService;
    private final List<ConsumerInterceptor<K, V>> interceptors;
    private final String topicName;
    
    public KafkaProxyConsumer(String topicName,
                             KafkaMetadataService metadataService,
                             Properties baseProperties) 
            throws KafkaMetadataService.MetadataServiceException {
        this.topicName = topicName;
        this.metadataService = metadataService;
        this.interceptors = new CopyOnWriteArrayList<>();
        
        // Get topic metadata and configure consumer
        TopicMetadata metadata = metadataService.getTopicMetadata(topicName)
            .orElseThrow(() -> new KafkaMetadataService.MetadataServiceException(
                "Topic metadata not found for: " + topicName));
        
        Properties consumerProps = buildConsumerProperties(baseProperties, metadata);
        this.delegate = new KafkaConsumer<>(consumerProps);
        
        logger.info("Created Kafka proxy consumer for topic: {}", topicName);
    }
    
    /**
     * Registers a consumer interceptor.
     * 
     * @param interceptor the interceptor to register
     */
    public void registerInterceptor(ConsumerInterceptor<K, V> interceptor) {
        interceptors.add(interceptor);
        logger.debug("Registered consumer interceptor: {}", interceptor.getClass().getSimpleName());
    }
    
    /**
     * Unregisters a consumer interceptor.
     * 
     * @param interceptor the interceptor to unregister
     */
    public void unregisterInterceptor(ConsumerInterceptor<K, V> interceptor) {
        interceptors.remove(interceptor);
        logger.debug("Unregistered consumer interceptor: {}", interceptor.getClass().getSimpleName());
    }
    
    @Override
    public Set<TopicPartition> assignment() {
        return delegate.assignment();
    }
    
    @Override
    public Set<String> subscription() {
        return delegate.subscription();
    }
    
    @Override
    public void subscribe(Collection<String> topics) {
        delegate.subscribe(topics);
    }
    
    @Override
    public void subscribe(Collection<String> topics, ConsumerRebalanceListener listener) {
        delegate.subscribe(topics, listener);
    }
    
    @Override
    public void subscribe(Pattern pattern, ConsumerRebalanceListener listener) {
        delegate.subscribe(pattern, listener);
    }
    
    @Override
    public void subscribe(Pattern pattern) {
        delegate.subscribe(pattern);
    }
    
    @Override
    public void unsubscribe() {
        delegate.unsubscribe();
    }
    
    @Override
    public void assign(Collection<TopicPartition> partitions) {
        delegate.assign(partitions);
    }
    
    @Override
    public ConsumerRecords<K, V> poll(long timeoutMs) {
        return poll(Duration.ofMillis(timeoutMs));
    }
    
    @Override
    public ConsumerRecords<K, V> poll(Duration timeout) {
        ConsumerRecords<K, V> records = delegate.poll(timeout);
        
        // Apply interceptors
        ConsumerRecords<K, V> processedRecords = records;
        for (ConsumerInterceptor<K, V> interceptor : interceptors) {
            try {
                processedRecords = interceptor.onConsume(processedRecords);
            } catch (Exception e) {
                logger.warn("Error in consumer interceptor onConsume", e);
            }
        }
        
        return processedRecords;
    }
    
    @Override
    public void commitSync() {
        delegate.commitSync();
    }
    
    @Override
    public void commitSync(Duration timeout) {
        delegate.commitSync(timeout);
    }
    
    @Override
    public void commitSync(Map<TopicPartition, OffsetAndMetadata> offsets) {
        delegate.commitSync(offsets);
    }
    
    @Override
    public void commitSync(Map<TopicPartition, OffsetAndMetadata> offsets, Duration timeout) {
        delegate.commitSync(offsets, timeout);
    }
    
    @Override
    public void commitAsync() {
        delegate.commitAsync();
    }
    
    @Override
    public void commitAsync(OffsetCommitCallback callback) {
        delegate.commitAsync(callback);
    }
    
    @Override
    public void commitAsync(Map<TopicPartition, OffsetAndMetadata> offsets, OffsetCommitCallback callback) {
        delegate.commitAsync(offsets, callback);
    }
    
    @Override
    public void seek(TopicPartition partition, long offset) {
        delegate.seek(partition, offset);
    }
    
    @Override
    public void seek(TopicPartition partition, OffsetAndMetadata offsetMetadata) {
        delegate.seek(partition, offsetMetadata);
    }
    
    @Override
    public void seekToBeginning(Collection<TopicPartition> partitions) {
        delegate.seekToBeginning(partitions);
    }
    
    @Override
    public void seekToEnd(Collection<TopicPartition> partitions) {
        delegate.seekToEnd(partitions);
    }
    
    @Override
    public long position(TopicPartition partition) {
        return delegate.position(partition);
    }
    
    @Override
    public long position(TopicPartition partition, Duration timeout) {
        return delegate.position(partition, timeout);
    }
    
    @Override
    public OffsetAndMetadata committed(TopicPartition partition) {
        return delegate.committed(partition);
    }
    
    @Override
    public OffsetAndMetadata committed(TopicPartition partition, Duration timeout) {
        return delegate.committed(partition, timeout);
    }
    
    @Override
    public Map<TopicPartition, OffsetAndMetadata> committed(Set<TopicPartition> partitions) {
        return delegate.committed(partitions);
    }
    
    @Override
    public Map<TopicPartition, OffsetAndMetadata> committed(Set<TopicPartition> partitions, Duration timeout) {
        return delegate.committed(partitions, timeout);
    }
    
    @Override
    public Map<MetricName, ? extends Metric> metrics() {
        return delegate.metrics();
    }
    
    @Override
    public List<PartitionInfo> partitionsFor(String topic) {
        return delegate.partitionsFor(topic);
    }
    
    @Override
    public List<PartitionInfo> partitionsFor(String topic, Duration timeout) {
        return delegate.partitionsFor(topic, timeout);
    }
    
    @Override
    public Map<String, List<PartitionInfo>> listTopics() {
        return delegate.listTopics();
    }
    
    @Override
    public Map<String, List<PartitionInfo>> listTopics(Duration timeout) {
        return delegate.listTopics(timeout);
    }
    
    @Override
    public Set<TopicPartition> paused() {
        return delegate.paused();
    }
    
    @Override
    public void pause(Collection<TopicPartition> partitions) {
        delegate.pause(partitions);
    }
    
    @Override
    public void resume(Collection<TopicPartition> partitions) {
        delegate.resume(partitions);
    }
    
    @Override
    public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Map<TopicPartition, Long> timestampsToSearch) {
        return delegate.offsetsForTimes(timestampsToSearch);
    }
    
    @Override
    public Map<TopicPartition, OffsetAndTimestamp> offsetsForTimes(Map<TopicPartition, Long> timestampsToSearch, Duration timeout) {
        return delegate.offsetsForTimes(timestampsToSearch, timeout);
    }
    
    @Override
    public Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> partitions) {
        return delegate.beginningOffsets(partitions);
    }
    
    @Override
    public Map<TopicPartition, Long> beginningOffsets(Collection<TopicPartition> partitions, Duration timeout) {
        return delegate.beginningOffsets(partitions, timeout);
    }
    
    @Override
    public Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> partitions) {
        return delegate.endOffsets(partitions);
    }
    
    @Override
    public Map<TopicPartition, Long> endOffsets(Collection<TopicPartition> partitions, Duration timeout) {
        return delegate.endOffsets(partitions, timeout);
    }
    
    @Override
    public OptionalLong currentLag(TopicPartition topicPartition) {
        return delegate.currentLag(topicPartition);
    }
    
    @Override
    public ConsumerGroupMetadata groupMetadata() {
        return delegate.groupMetadata();
    }
    
    @Override
    public void enforceRebalance() {
        delegate.enforceRebalance();
    }
    
    @Override
    public void enforceRebalance(String reason) {
        delegate.enforceRebalance(reason);
    }
    
    @Override
    public void close() {
        close(Duration.ofSeconds(30));
    }
    
    @Override
    public void close(Duration timeout) {
        logger.info("Closing Kafka proxy consumer for topic: {}", topicName);
        
        // Close interceptors
        for (ConsumerInterceptor<K, V> interceptor : interceptors) {
            try {
                interceptor.close();
            } catch (Exception e) {
                logger.warn("Error closing consumer interceptor", e);
            }
        }
        
        delegate.close(timeout);
    }
    
    @Override
    public void wakeup() {
        delegate.wakeup();
    }
    
    @Override
    public org.apache.kafka.common.Uuid clientInstanceId(Duration timeout) {
        return delegate.clientInstanceId(timeout);
    }
    
    private Properties buildConsumerProperties(Properties baseProperties, TopicMetadata metadata) {
        Properties props = new Properties(baseProperties);
        
        // Build bootstrap servers from broker info
        StringBuilder bootstrapServers = new StringBuilder();
        for (int i = 0; i < metadata.brokers().size(); i++) {
            if (i > 0) bootstrapServers.append(",");
            TopicMetadata.BrokerInfo broker = metadata.brokers().get(i);
            bootstrapServers.append(broker.host()).append(":").append(broker.port());
        }
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers.toString());
        
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
