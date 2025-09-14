package com.example.kafka.proxy.interceptors;

import com.example.kafka.proxy.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer interceptor that logs all producer operations.
 * 
 * @param <K> the key type
 * @param <V> the value type
 */
public class LoggingProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingProducerInterceptor.class);
    
    private final String name;
    private final boolean logPayload;
    
    public LoggingProducerInterceptor() {
        this("default", false);
    }
    
    public LoggingProducerInterceptor(String name, boolean logPayload) {
        this.name = name;
        this.logPayload = logPayload;
    }
    
    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        if (logPayload) {
            logger.info("[{}] Sending record to topic: {}, partition: {}, key: {}, value: {}",
                name, record.topic(), record.partition(), record.key(), record.value());
        } else {
            logger.info("[{}] Sending record to topic: {}, partition: {}, timestamp: {}",
                name, record.topic(), record.partition(), record.timestamp());
        }
        
        return record;
    }
    
    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        if (exception != null) {
            logger.error("[{}] Failed to send record: {}", name, exception.getMessage());
        } else if (metadata != null) {
            logger.info("[{}] Record acknowledged - topic: {}, partition: {}, offset: {}, timestamp: {}",
                name, metadata.topic(), metadata.partition(), metadata.offset(), metadata.timestamp());
        } else {
            logger.info("[{}] Record acknowledgment received (no metadata)", name);
        }
    }
    
    @Override
    public void close() {
        logger.info("[{}] Closing logging producer interceptor", name);
    }
}
