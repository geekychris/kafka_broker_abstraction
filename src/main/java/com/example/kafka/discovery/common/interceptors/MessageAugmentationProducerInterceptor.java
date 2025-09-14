package com.example.kafka.discovery.common.interceptors;

import com.example.kafka.discovery.common.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.function.Function;

/**
 * Producer interceptor that augments messages with additional metadata.
 * 
 * @param <K> the key type
 * @param <V> the value type
 */
public class MessageAugmentationProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageAugmentationProducerInterceptor.class);
    
    private final Function<V, V> messageTransformer;
    private final boolean addTimestamp;
    private final boolean addClientId;
    private final String clientId;
    
    public MessageAugmentationProducerInterceptor() {
        this(Function.identity(), true, true, "kafka-proxy-client");
    }
    
    public MessageAugmentationProducerInterceptor(Function<V, V> messageTransformer, 
                                                 boolean addTimestamp, 
                                                 boolean addClientId,
                                                 String clientId) {
        this.messageTransformer = messageTransformer;
        this.addTimestamp = addTimestamp;
        this.addClientId = addClientId;
        this.clientId = clientId;
    }
    
    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        // Transform the message value
        V transformedValue = messageTransformer.apply(record.value());
        
        // Create new record with potentially modified value
        ProducerRecord<K, V> newRecord = new ProducerRecord<>(
            record.topic(),
            record.partition(),
            record.timestamp(),
            record.key(),
            transformedValue,
            record.headers()
        );
        
        // Add headers for metadata
        Headers headers = newRecord.headers();
        
        if (addTimestamp) {
            headers.add("proxy.timestamp", Instant.now().toString().getBytes());
        }
        
        if (addClientId) {
            headers.add("proxy.client-id", clientId.getBytes());
        }
        
        // Add a unique message ID
        headers.add("proxy.message-id", java.util.UUID.randomUUID().toString().getBytes());
        
        logger.debug("Augmented message for topic: {} with {} headers", 
            record.topic(), headers.toArray().length);
        
        return newRecord;
    }
    
    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // No special handling needed for acknowledgements in this interceptor
        if (exception != null) {
            logger.debug("Message augmentation interceptor noted send failure: {}", exception.getMessage());
        }
    }
    
    @Override
    public void close() {
        logger.info("Closing message augmentation producer interceptor");
    }
}
