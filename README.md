# Kafka Proxy Client

A Java library that provides a proxy wrapper for Kafka clients, hiding the complexities of topic configuration while offering extensible interceptor capabilities for logging and message augmentation.

## Features

- **Metadata Service Integration**: Abstracts broker discovery and security configuration through a pluggable metadata service
- **Security Configuration**: Supports PLAINTEXT, SSL, SASL_PLAINTEXT, and SASL_SSL with mutual authentication
- **Interceptor Framework**: Extensible callback system for both producers and consumers
- **Built-in Interceptors**: 
  - Logging interceptor for comprehensive operation monitoring
  - Message augmentation interceptor for adding metadata headers
- **Thread-Safe**: Designed for concurrent usage in multi-threaded applications

## Architecture

The proxy client consists of several key components:

1. **KafkaMetadataService**: Interface for retrieving topic metadata including broker info and security config
2. **KafkaProxyProducer/Consumer**: Wrapper classes that delegate to actual Kafka clients while providing interceptor support
3. **Interceptor Framework**: Producer and consumer interceptors for extending functionality
4. **Built-in Interceptors**: Ready-to-use implementations for common use cases

## Quick Start

### 1. Create a Metadata Service

```java
// For development/testing
StubKafkaMetadataService metadataService = new StubKafkaMetadataService();

// Add custom topic metadata
TopicMetadata myTopic = new TopicMetadata(
    "my-topic",
    List.of(
        new TopicMetadata.BrokerInfo(1, "kafka1.example.com", 9092, true),
        new TopicMetadata.BrokerInfo(2, "kafka2.example.com", 9092, false)
    ),
    TopicMetadata.SecurityConfig.ssl(
        "/path/to/keystore.jks", "keystorepass",
        "/path/to/truststore.jks", "truststorepass"
    ),
    Map.of("replication.factor", "2")
);
metadataService.addTopicMetadata(myTopic);
```

### 2. Create a Proxy Producer

```java
Properties producerProps = new Properties();
producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

KafkaProxyProducer<String, String> producer = new KafkaProxyProducer<>(
    "my-topic", metadataService, producerProps);

// Register interceptors
producer.registerInterceptor(new LoggingProducerInterceptor<>("my-producer", false));
producer.registerInterceptor(new MessageAugmentationProducerInterceptor<>(
    value -> "[PROCESSED] " + value, // Message transformer
    true,  // Add timestamp
    true,  // Add client ID
    "my-app"
));

// Send messages
producer.send(new ProducerRecord<>("my-topic", "key1", "Hello World!"));
producer.close();
```

### 3. Create a Proxy Consumer

```java
Properties consumerProps = new Properties();
consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "my-group");

KafkaProxyConsumer<String, String> consumer = new KafkaProxyConsumer<>(
    "my-topic", metadataService, consumerProps);

// Register interceptors
consumer.registerInterceptor(new LoggingConsumerInterceptor<>("my-consumer", false));

// Subscribe and poll
consumer.subscribe(Collections.singletonList("my-topic"));
ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
consumer.close();
```

## Security Configuration

The proxy supports various security protocols:

### PLAINTEXT
```java
TopicMetadata.SecurityConfig.plaintext()
```

### SSL with Mutual Authentication
```java
TopicMetadata.SecurityConfig.ssl(
    "/path/to/client.keystore.jks", "keystorePassword",
    "/path/to/client.truststore.jks", "truststorePassword"
);
```

### Custom Security Configuration
```java
new TopicMetadata.SecurityConfig(
    SecurityProtocol.SASL_SSL,
    keystorePath, keystorePassword,
    truststorePath, truststorePassword,
    "SCRAM-SHA-256",  // SASL mechanism
    "username", "password",
    Map.of("sasl.jaas.config", "...", "ssl.endpoint.identification.algorithm", "")
);
```

## Custom Interceptors

### Producer Interceptor
```java
public class CustomProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {
    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        // Transform record before sending
        return record;
    }
    
    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // Handle acknowledgment or error
    }
    
    @Override
    public void close() {
        // Cleanup resources
    }
}
```

### Consumer Interceptor
```java
public class CustomConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {
    @Override
    public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
        // Process records after polling
        return records;
    }
    
    @Override
    public void onCommit(ConsumerRecords<K, V> records) {
        // Handle commit events
    }
    
    @Override
    public void close() {
        // Cleanup resources
    }
}
```

## Building and Testing

Build the project:
```bash
mvn clean compile
```

Run tests:
```bash
mvn test
```

Run the example:
```bash
mvn exec:java -Dexec.mainClass="com.example.kafka.proxy.KafkaProxyExample"
```

## Requirements

- Java 21 or higher
- Apache Kafka clients 3.7.0
- SLF4J for logging

## License

This project is provided as an example implementation. Adapt as needed for your specific requirements.
