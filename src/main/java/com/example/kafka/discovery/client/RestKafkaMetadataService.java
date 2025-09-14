package com.example.kafka.discovery.client;

import com.example.kafka.discovery.common.KafkaMetadataService;
import com.example.kafka.discovery.common.TopicMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * REST-based implementation of KafkaMetadataService that calls a broker discovery service.
 */
public class RestKafkaMetadataService implements KafkaMetadataService {
    
    private static final Logger logger = LoggerFactory.getLogger(RestKafkaMetadataService.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final OkHttpClient httpClient;
    private final RestClientConfig config;
    private final ObjectMapper objectMapper;
    
    public RestKafkaMetadataService(RestClientConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = buildHttpClient(config);
        logger.info("Initialized REST metadata service with base URL: {}", config.baseUrl());
    }
    
    @Override
    public Optional<TopicMetadata> getTopicMetadata(String topicName) throws MetadataServiceException {
        String url = config.baseUrl() + "/api/topics/" + topicName;
        Request request = buildRequest(url)
            .get()
            .build();
        
        return executeWithRetry(request, response -> {
            if (response.code() == 404) {
                logger.debug("Topic metadata not found for: {}", topicName);
                return Optional.empty();
            }
            
            if (!response.isSuccessful()) {
                throw new MetadataServiceException(
                    "Failed to get topic metadata: HTTP " + response.code() + " " + response.message());
            }
            
            try (ResponseBody responseBody = response.body()) {
                if (responseBody == null) {
                    throw new MetadataServiceException("Empty response body");
                }
                
                String json = responseBody.string();
                TopicMetadata metadata = objectMapper.readValue(json, TopicMetadata.class);
                logger.debug("Retrieved metadata for topic: {}", topicName);
                return Optional.of(metadata);
                
            } catch (IOException e) {
                throw new MetadataServiceException("Failed to parse response", e);
            }
        });
    }
    
    @Override
    public boolean isHealthy() {
        try {
            String url = config.baseUrl() + "/health";
            Request request = buildRequest(url)
                .get()
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                boolean healthy = response.isSuccessful();
                logger.debug("Health check result: {}", healthy);
                return healthy;
            }
        } catch (Exception e) {
            logger.warn("Health check failed", e);
            return false;
        }
    }
    
    @Override
    public void refreshTopicMetadata(String topicName) throws MetadataServiceException {
        String url = config.baseUrl() + "/api/topics/" + topicName + "/refresh";
        Request request = buildRequest(url)
            .post(RequestBody.create("", JSON))
            .build();
        
        executeWithRetry(request, response -> {
            if (!response.isSuccessful()) {
                throw new MetadataServiceException(
                    "Failed to refresh topic metadata: HTTP " + response.code() + " " + response.message());
            }
            logger.info("Successfully refreshed metadata for topic: {}", topicName);
            return null;
        });
    }
    
    /**
     * Closes the HTTP client and releases resources.
     */
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        logger.info("Closed REST metadata service");
    }
    
    private OkHttpClient buildHttpClient(RestClientConfig config) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(config.connectTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .writeTimeout(config.writeTimeout().toMillis(), TimeUnit.MILLISECONDS);
        
        // Configure SSL if provided
        if (config.sslContext() != null) {
            try {
                javax.net.ssl.TrustManagerFactory trustManagerFactory = 
                    javax.net.ssl.TrustManagerFactory.getInstance(
                        javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init((java.security.KeyStore) null);
                
                X509TrustManager trustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
                builder.sslSocketFactory(config.sslContext().getSocketFactory(), trustManager);
            } catch (Exception e) {
                logger.warn("Failed to configure SSL context, using default", e);
            }
        }
        
        if (!config.verifyHostname()) {
            builder.hostnameVerifier((hostname, session) -> true);
        }
        
        // Add authentication interceptor if needed
        if (config.username() != null || config.bearerToken() != null) {
            builder.addInterceptor(new AuthenticationInterceptor(config));
        }
        
        return builder.build();
    }
    
    private Request.Builder buildRequest(String url) {
        return new Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "kafka-proxy-client/1.0");
    }
    
    private <T> T executeWithRetry(Request request, ResponseHandler<T> handler) throws MetadataServiceException {
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= config.maxRetries(); attempt++) {
            try {
                try (Response response = httpClient.newCall(request).execute()) {
                    return handler.handle(response);
                }
            } catch (IOException e) {
                lastException = e;
                if (attempt < config.maxRetries()) {
                    logger.warn("Request attempt {} failed, retrying in {}ms", 
                        attempt + 1, config.retryDelay().toMillis());
                    try {
                        Thread.sleep(config.retryDelay().toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new MetadataServiceException("Request interrupted", ie);
                    }
                }
            } catch (MetadataServiceException e) {
                // Don't retry on application errors
                throw e;
            } catch (Exception e) {
                lastException = e;
                break;
            }
        }
        
        throw new MetadataServiceException(
            "Failed after " + (config.maxRetries() + 1) + " attempts", lastException);
    }
    
    @FunctionalInterface
    private interface ResponseHandler<T> {
        T handle(Response response) throws MetadataServiceException, IOException;
    }
    
    private static class AuthenticationInterceptor implements Interceptor {
        private final RestClientConfig config;
        
        public AuthenticationInterceptor(RestClientConfig config) {
            this.config = config;
        }
        
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request.Builder requestBuilder = chain.request().newBuilder();
            
            if (config.bearerToken() != null) {
                requestBuilder.addHeader("Authorization", "Bearer " + config.bearerToken());
            } else if (config.username() != null) {
                String credentials = config.username() + ":" + (config.password() != null ? config.password() : "");
                String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes());
                requestBuilder.addHeader("Authorization", "Basic " + basicAuth);
            }
            
            return chain.proceed(requestBuilder.build());
        }
    }
}
