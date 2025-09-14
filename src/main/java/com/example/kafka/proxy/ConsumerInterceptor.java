package com.example.kafka.proxy;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

/**
 * Interceptor for consumer operations allowing message transformation and monitoring.
 * 
 * @param <K> the key type
 * @param <V> the value type
 */
public interface ConsumerInterceptor<K, V> {
    
    /**
     * Called after records are polled from Kafka but before they're returned to the application.
     * Allows modification of records.
     * 
     * @param records the records that were polled
     * @return the modified records (can be the same instance if no changes needed)
     */
    ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records);
    
    /**
     * Called before records are committed (for auto-commit or manual commit).
     * 
     * @param records the records that are about to be committed
     */
    void onCommit(ConsumerRecords<K, V> records);
    
    /**
     * Called when the interceptor is closed.
     */
    default void close() {}
}
