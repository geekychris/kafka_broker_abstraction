package com.example.kafka.proxy;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * Interceptor for producer operations allowing message transformation and monitoring.
 * 
 * @param <K> the key type
 * @param <V> the value type
 */
public interface ProducerInterceptor<K, V> {
    
    /**
     * Called before sending a record to Kafka.
     * Allows modification of the record before it's sent.
     * 
     * @param record the record to be sent
     * @return the modified record (can be the same instance if no changes needed)
     */
    ProducerRecord<K, V> onSend(ProducerRecord<K, V> record);
    
    /**
     * Called when a record has been acknowledged by the server or when send fails.
     * 
     * @param metadata the metadata for the record that was sent (null if send failed)
     * @param exception exception thrown during processing (null if successful)
     */
    void onAcknowledgement(RecordMetadata metadata, Exception exception);
    
    /**
     * Called when the interceptor is closed.
     */
    default void close() {}
}
