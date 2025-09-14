package com.example.kafka.discovery.client;

import javax.net.ssl.SSLContext;
import java.time.Duration;

/**
 * Configuration for REST client connecting to the broker discovery service.
 */
public record RestClientConfig(
    String baseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    Duration writeTimeout,
    boolean verifyHostname,
    SSLContext sslContext,
    String username,
    String password,
    String bearerToken,
    int maxRetries,
    Duration retryDelay
) {
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String baseUrl;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        private Duration writeTimeout = Duration.ofSeconds(10);
        private boolean verifyHostname = true;
        private SSLContext sslContext;
        private String username;
        private String password;
        private String bearerToken;
        private int maxRetries = 3;
        private Duration retryDelay = Duration.ofSeconds(1);
        
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }
        
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }
        
        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }
        
        public Builder writeTimeout(Duration timeout) {
            this.writeTimeout = timeout;
            return this;
        }
        
        public Builder verifyHostname(boolean verify) {
            this.verifyHostname = verify;
            return this;
        }
        
        public Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }
        
        public Builder basicAuth(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }
        
        public Builder bearerToken(String token) {
            this.bearerToken = token;
            return this;
        }
        
        public Builder maxRetries(int retries) {
            this.maxRetries = retries;
            return this;
        }
        
        public Builder retryDelay(Duration delay) {
            this.retryDelay = delay;
            return this;
        }
        
        public RestClientConfig build() {
            if (baseUrl == null) {
                throw new IllegalArgumentException("Base URL is required");
            }
            return new RestClientConfig(
                baseUrl, connectTimeout, readTimeout, writeTimeout,
                verifyHostname, sslContext, username, password, bearerToken,
                maxRetries, retryDelay
            );
        }
    }
}
