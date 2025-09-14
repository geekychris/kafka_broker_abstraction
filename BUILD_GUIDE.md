# Kafka Broker Discovery System - Build & Integration Guide

## Table of Contents
- [Prerequisites](#prerequisites)
- [Building the System](#building-the-system)
- [Running the Discovery Service](#running-the-discovery-service)
- [Client Integration](#client-integration)
- [Testing](#testing)
- [Local Development Setup](#local-development-setup)
- [Production Deployment](#production-deployment)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### Software Requirements
- **Java**: OpenJDK 21+ (tested with OpenJDK 21.0.6 and Amazon Corretto 23)
- **Maven**: 3.9+ (for building and dependency management)
- **Docker**: Optional, for running local Kafka cluster
- **Docker Compose**: Optional, for orchestrated local environment

### Environment Variables
Ensure your JAVA_HOME is properly set:
```bash
# For macOS with Homebrew OpenJDK
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

# For Amazon Corretto
export JAVA_HOME=/path/to/amazon-corretto-23.jdk/Contents/Home
```

### Verify Prerequisites
```bash
# Check Java version
java -version
# Should show: openjdk version "21.0.x" or later

# Check Maven version  
mvn -version
# Should show: Apache Maven 3.9.x or later

# Check Docker (optional)
docker --version
docker-compose --version
```

## Building the System

### 1. Clone and Build
```bash
# Navigate to project directory
cd kafka-proxy

# Clean and compile the project
mvn clean compile

# Run tests to verify build
mvn test

# Package the JAR file
mvn package
```

### 2. Build Output
After successful build, you'll find:
```
target/
├── kafka-proxy-1.0-SNAPSHOT.jar          # Main application JAR
├── classes/                               # Compiled classes
└── test-classes/                          # Compiled test classes
```

### 3. Build with Specific Profiles
```bash
# Build with production profile (if configured)
mvn clean package -Pproduction

# Skip tests for faster builds
mvn clean package -DskipTests

# Generate sources and javadoc
mvn clean package source:jar javadoc:jar
```

## Running the Discovery Service

### 1. Start the Discovery Service

#### Option A: Using Maven
```bash
# Run with default port (8080)
mvn exec:java -Dexec.mainClass="com.example.kafka.discovery.server.DiscoveryServerApplication"

# Run with custom port
mvn exec:java -Dexec.mainClass="com.example.kafka.discovery.server.DiscoveryServerApplication" -Dexec.args="9876"
```

#### Option B: Using JAR
```bash
# Build the JAR first
mvn clean package

# Run with default port
java -cp target/kafka-proxy-1.0-SNAPSHOT.jar com.example.kafka.discovery.server.DiscoveryServerApplication

# Run with custom port
java -cp target/kafka-proxy-1.0-SNAPSHOT.jar com.example.kafka.discovery.server.DiscoveryServerApplication 9876
```

#### Option C: Using BrokerDiscoveryService Directly
```java
public class MyDiscoveryServer {
    public static void main(String[] args) {
        BrokerDiscoveryService service = new BrokerDiscoveryService(9876);
        service.start();
        
        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(service::stop));
        
        // Keep server running
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            service.stop();
        }
    }
}
```

### 2. Verify Service is Running
```bash
# Check health endpoint
curl -X GET http://localhost:9876/health
# Expected response: {"status":"healthy","timestamp":...}

# List available topics  
curl -X GET http://localhost:9876/api/topics
# Expected response: ["dev-topic","prod-topic","staging-topic"]

# Get specific topic metadata
curl -X GET http://localhost:9876/api/topics/dev-topic
# Expected response: Topic metadata JSON
```

## Client Integration

### 1. Maven Dependency
Add to your project's `pom.xml`:
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>kafka-proxy</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. Basic Client Setup
```java
import com.example.kafka.discovery.client.KafkaDiscoveryClient;
import com.example.kafka.discovery.client.RestClientConfig;

// Create discovery client
RestClientConfig config = RestClientConfig.builder()
    .baseUrl("http://localhost:9876")
    .connectTimeout(Duration.ofSeconds(5))
    .readTimeout(Duration.ofSeconds(10))
    .build();

KafkaDiscoveryClient discoveryClient = new KafkaDiscoveryClient(config);
```

### 3. Producer Integration
```java
// Create producer with automatic broker discovery
KafkaProducer<String, String> producer = discoveryClient.createProducer(
    "dev-topic",
    String.class,
    String.class,
    true  // enable debug interceptors
);

// Use producer normally
producer.send(new ProducerRecord<>("dev-topic", "key", "value"));
producer.close();
```

### 4. Consumer Integration
```java
// Create consumer with automatic broker discovery
KafkaConsumer<String, String> consumer = discoveryClient.createConsumer(
    "dev-topic", 
    "my-consumer-group",
    String.class,
    String.class,
    true  // enable debug interceptors
);

// Subscribe and consume
consumer.subscribe(Arrays.asList("dev-topic"));
while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    for (ConsumerRecord<String, String> record : records) {
        System.out.printf("Consumed: key=%s, value=%s%n", record.key(), record.value());
    }
}
```

### 5. Advanced Configuration
```java
// Custom REST client configuration
RestClientConfig config = RestClientConfig.builder()
    .baseUrl("https://discovery-service.company.com")
    .connectTimeout(Duration.ofSeconds(10))
    .readTimeout(Duration.ofSeconds(30))
    .maxRetries(5)
    .retryDelay(Duration.ofSeconds(2))
    // SSL configuration
    .sslContext(mySslContext)
    .verifyHostname(false)
    // Authentication
    .username("api-user")
    .password("api-password")
    .build();

KafkaDiscoveryClient client = new KafkaDiscoveryClient(config);
```

### 6. Health Monitoring
```java
// Check if discovery service is healthy
if (discoveryClient.isServiceHealthy()) {
    // Proceed with Kafka operations
    KafkaProducer<String, String> producer = discoveryClient.createProducer("my-topic", String.class, String.class);
} else {
    // Handle discovery service unavailability
    logger.error("Discovery service is not available");
}
```

## Testing

### 1. Unit Tests
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=BrokerDiscoveryIntegrationTest

# Run with verbose output
mvn test -Dtest=BrokerDiscoveryIntegrationTest -DforkMode=never -Dmaven.surefire.debug
```

### 2. Integration Tests
```bash
# Start the discovery service first
java -cp target/kafka-proxy-1.0-SNAPSHOT.jar com.example.kafka.discovery.server.DiscoveryServerApplication 9876

# In another terminal, run integration tests
mvn test -Dtest=*IntegrationTest
```

### 3. Manual Testing with cURL
```bash
# Health check
curl -X GET http://localhost:9876/health

# Get topic metadata
curl -X GET http://localhost:9876/api/topics/dev-topic

# Create new topic metadata
curl -X POST http://localhost:9876/api/topics \
  -H "Content-Type: application/json" \
  -d '{
    "topicName": "test-topic",
    "brokers": [{"id": 1, "host": "localhost", "port": 9092, "isController": true}],
    "securityConfig": {"protocol": "PLAINTEXT"},
    "topicProperties": {"replication.factor": "1"}
  }'

# Update topic metadata
curl -X PUT http://localhost:9876/api/topics/test-topic \
  -H "Content-Type: application/json" \
  -d '{"topicName": "test-topic", "brokers": [{"id": 1, "host": "new-host", "port": 9093, "isController": true}], "securityConfig": {"protocol": "PLAINTEXT"}, "topicProperties": {}}'

# Delete topic metadata
curl -X DELETE http://localhost:9876/api/topics/test-topic
```

### 4. Full Integration Example
```bash
# Run the full integration example
mvn exec:java -Dexec.mainClass="com.example.kafka.examples.FullIntegrationExample"
```

## Local Development Setup

### 1. Start Local Kafka Cluster with Docker Compose
```bash
# Start Kafka cluster (Zookeeper + Kafka + UI)
docker-compose up -d

# Verify services are running
docker-compose ps

# View logs
docker-compose logs kafka
```

### 2. Create Test Topics
```bash
# Connect to Kafka container
docker exec -it kafka-proxy-kafka-1 bash

# Create test topics
kafka-topics.sh --create --topic dev-topic --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
kafka-topics.sh --create --topic prod-topic --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
kafka-topics.sh --create --topic staging-topic --bootstrap-server localhost:9092 --partitions 2 --replication-factor 1
```

### 3. Test with Real Kafka
```java
public class LocalTestExample {
    public static void main(String[] args) throws Exception {
        // Start discovery service
        BrokerDiscoveryService service = new BrokerDiscoveryService(9876);
        service.start();
        
        // Create discovery client
        RestClientConfig config = RestClientConfig.builder()
            .baseUrl("http://localhost:9876")
            .build();
        KafkaDiscoveryClient client = new KafkaDiscoveryClient(config);
        
        // Test producer
        try (KafkaProducer<String, String> producer = client.createProducer("dev-topic", String.class, String.class)) {
            producer.send(new ProducerRecord<>("dev-topic", "test-key", "test-value")).get();
            System.out.println("Message sent successfully!");
        }
        
        // Test consumer
        try (KafkaConsumer<String, String> consumer = client.createConsumer("dev-topic", "test-group", String.class, String.class)) {
            consumer.subscribe(Arrays.asList("dev-topic"));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
            System.out.println("Consumed " + records.count() + " messages");
        }
        
        service.stop();
    }
}
```

### 4. Kafka UI Access
Access Kafka UI at http://localhost:8080 to:
- View topics and partitions
- Monitor consumer groups
- Browse messages
- View broker information

## Production Deployment

### 1. Building for Production
```bash
# Create production-ready JAR with all dependencies
mvn clean package -DskipTests

# Or create a fat JAR (if configured)
mvn clean package shade:shade
```

### 2. Configuration Management
```bash
# Use environment variables for configuration
export DISCOVERY_SERVICE_PORT=8080
export KAFKA_BOOTSTRAP_SERVERS=kafka1:9092,kafka2:9092,kafka3:9092
export LOG_LEVEL=INFO

# Run with production settings
java -Xms512m -Xmx2g \
     -Dserver.port=${DISCOVERY_SERVICE_PORT} \
     -Dlog.level=${LOG_LEVEL} \
     -cp target/kafka-proxy-1.0-SNAPSHOT.jar \
     com.example.kafka.discovery.server.DiscoveryServerApplication
```

### 3. Docker Deployment
Create a `Dockerfile`:
```dockerfile
FROM openjdk:21-jre-slim

WORKDIR /app
COPY target/kafka-proxy-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
```

Build and run:
```bash
# Build Docker image
docker build -t kafka-discovery-service .

# Run container
docker run -p 8080:8080 kafka-discovery-service

# Run with custom port
docker run -p 9876:9876 kafka-discovery-service 9876
```

### 4. Health Checks
Configure health check endpoints:
```yaml
# Docker Compose health check
services:
  discovery-service:
    image: kafka-discovery-service
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

### 5. Monitoring Setup
```bash
# Add JVM metrics
java -Dcom.sun.management.jmxremote \
     -Dcom.sun.management.jmxremote.port=9999 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false \
     -jar target/kafka-proxy-1.0-SNAPSHOT.jar
```

## Troubleshooting

### Common Build Issues

#### Issue: "Package does not exist" errors
```
Solution: Clean and rebuild
mvn clean compile
```

#### Issue: Java version mismatch
```
Error: UnsupportedClassVersionError
Solution: Ensure Java 21+ is used
java -version
export JAVA_HOME=/path/to/java21
```

#### Issue: Maven compilation warnings
```
Warning: location of system modules is not set
Solution: Add --release flag (already configured in pom.xml)
```

### Common Runtime Issues

#### Issue: "Address already in use"
```
Error: java.net.BindException: Address already in use
Solution: Change port or stop conflicting service
lsof -i :8080  # Find what's using the port
kill -9 <PID>  # Stop the process
```

#### Issue: Connection refused from client
```
Error: java.net.ConnectException: Connection refused
Solution: Verify discovery service is running and port is correct
curl -X GET http://localhost:9876/health
```

#### Issue: SSL handshake failures
```
Error: javax.net.ssl.SSLHandshakeException
Solution: Check SSL configuration and certificates
keytool -list -keystore /path/to/keystore.jks
```

### Performance Tuning

#### JVM Tuning
```bash
# For high-throughput scenarios
java -Xms1g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=100 \
     -XX:+DisableExplicitGC \
     -jar target/kafka-proxy-1.0-SNAPSHOT.jar
```

#### HTTP Client Tuning
```java
RestClientConfig config = RestClientConfig.builder()
    .baseUrl("http://discovery-service")
    .connectTimeout(Duration.ofSeconds(5))
    .readTimeout(Duration.ofSeconds(30))
    .maxRetries(3)
    .retryDelay(Duration.ofMillis(500))
    .build();
```

### Debugging

#### Enable Debug Logging
```bash
# Run with debug logging
java -Dlogging.level.com.example.kafka=DEBUG \
     -jar target/kafka-proxy-1.0-SNAPSHOT.jar
```

#### Network Debugging
```bash
# Test network connectivity
telnet localhost 9876

# Check DNS resolution
nslookup discovery-service.company.com

# Monitor HTTP traffic
curl -v http://localhost:9876/health
```

#### Java Debugging
```bash
# Remote debugging
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -jar target/kafka-proxy-1.0-SNAPSHOT.jar

# Then connect your IDE to port 5005
```

## Support and Documentation

- **Architecture**: See [ARCHITECTURE.md](ARCHITECTURE.md)
- **API Documentation**: Available at `http://localhost:9876/api` when service is running
- **Source Code**: Well-documented with Javadoc comments
- **Examples**: See `src/main/java/com/example/kafka/examples/`
- **Tests**: See `src/test/java/` for comprehensive test examples
