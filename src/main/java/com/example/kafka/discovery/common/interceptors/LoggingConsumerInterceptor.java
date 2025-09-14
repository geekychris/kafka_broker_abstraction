package com.example.kafka.discovery.common.interceptors;

import com.example.kafka.discovery.common.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer interceptor that logs all consumer operations.
 * 
 * @param <K> the key type
 * @param <V> the value type
 */
public class LoggingConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingConsumerInterceptor.class);
    
    private final String name;
    private final boolean logPayload;
    
    public LoggingConsumerInterceptor() {
        this("default", false);
    }
    
    public LoggingConsumerInterceptor(String name, boolean logPayload) {
        this.name = name;
        this.logPayload = logPayload;
    }
    
    @Override
    public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
        logger.info("[{}] Consumed {} records", name, records.count());
        
        if (logPayload) {
            for (ConsumerRecord<K, V> record : records) {
                logger.info("[{}] Record - topic: {}, partition: {}, offset: {}, key: {}, value: {}",
                    name, record.topic(), record.partition(), record.offset(), record.key(), record.value());
            }
        } else {
            for (ConsumerRecord<K, V> record : records) {
                logger.info("[{}] Record - topic: {}, partition: {}, offset: {}, timestamp: {}",
                    name, record.topic(), record.partition(), record.offset(), record.timestamp());
            }
        }
        
        return records;
    }
    
    @Override
    public void onCommit(ConsumerRecords<K, V> records) {
        logger.info("[{}] Committing {} records", name, records.count());
    }
    
    @Override
    public void close() {
        logger.info("[{}] Closing logging consumer interceptor", name);
    }
}
