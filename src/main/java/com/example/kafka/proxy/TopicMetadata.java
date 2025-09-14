package com.example.kafka.proxy;

import java.util.List;
import java.util.Map;

/**
 * Represents metadata for a Kafka topic including broker information and security configuration.
 */
public record TopicMetadata(
    String topicName,
    List<BrokerInfo> brokers,
    SecurityConfig securityConfig,
    Map<String, String> additionalProperties
) {
    
    /**
     * Represents information about a Kafka broker.
     */
    public record BrokerInfo(
        int id,
        String host,
        int port,
        boolean isController
    ) {}
    
    /**
     * Represents security configuration for connecting to a topic.
     */
    public record SecurityConfig(
        SecurityProtocol protocol,
        String keystorePath,
        String keystorePassword,
        String truststorePath,
        String truststorePassword,
        String saslMechanism,
        String saslUsername,
        String saslPassword,
        Map<String, String> additionalSecurityProps
    ) {
        
        public enum SecurityProtocol {
            PLAINTEXT,
            SSL,
            SASL_PLAINTEXT,
            SASL_SSL
        }
        
        /**
         * Creates a basic PLAINTEXT security configuration.
         */
        public static SecurityConfig plaintext() {
            return new SecurityConfig(
                SecurityProtocol.PLAINTEXT,
                null, null, null, null,
                null, null, null,
                Map.of()
            );
        }
        
        /**
         * Creates an SSL configuration with mutual authentication.
         */
        public static SecurityConfig ssl(String keystorePath, String keystorePassword,
                                       String truststorePath, String truststorePassword) {
            return new SecurityConfig(
                SecurityProtocol.SSL,
                keystorePath, keystorePassword,
                truststorePath, truststorePassword,
                null, null, null,
                Map.of()
            );
        }
    }
}
