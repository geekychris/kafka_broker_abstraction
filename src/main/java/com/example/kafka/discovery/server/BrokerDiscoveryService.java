package com.example.kafka.discovery.server;

import com.example.kafka.discovery.common.TopicMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import spark.Request;
import spark.Response;

import static spark.Spark.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * REST API service for broker discovery that provides topic metadata endpoints.
 */
public class BrokerDiscoveryService {
    
    private static final Logger logger = LoggerFactory.getLogger(BrokerDiscoveryService.class);
    
    private final TopicMetadataStore metadataStore;
    private final ObjectMapper objectMapper;
    private final int port;
    private boolean running = false;
    
    public BrokerDiscoveryService(int port) {
        this.port = port;
        this.metadataStore = new TopicMetadataStore();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Starts the broker discovery service.
     */
    public void start() {
        if (running) {
            throw new IllegalStateException("Service is already running");
        }
        
        port(port);
        
        // Configure CORS for cross-origin requests
        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept");
            response.type("application/json");
        });
        
        // Handle preflight OPTIONS requests
        options("/*", (request, response) -> {
            response.status(200);
            return "";
        });
        
        // Health check endpoint
        get("/health", this::healthCheck);
        
        // Topic metadata endpoints
        get("/api/topics", this::listTopics);
        get("/api/topics/:name", this::getTopicMetadata);
        post("/api/topics", this::createTopicMetadata);
        put("/api/topics/:name", this::updateTopicMetadata);
        delete("/api/topics/:name", this::deleteTopicMetadata);
        post("/api/topics/:name/refresh", this::refreshTopicMetadata);
        
        // Admin endpoints
        get("/api/admin/topics", this::getAllTopicMetadata);
        delete("/api/admin/topics", this::clearAllTopics);
        get("/api/admin/stats", this::getStats);
        
        // Exception handling
        exception(Exception.class, this::handleException);
        
        // Wait for the server to start
        awaitInitialization();
        running = true;
        
        logger.info("Broker Discovery Service started on port {}", port);
    }
    
    /**
     * Stops the broker discovery service.
     */
    public void stop() {
        if (running) {
            spark.Spark.stop();
            running = false;
            logger.info("Broker Discovery Service stopped");
        }
    }
    
    /**
     * Returns whether the service is running.
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Gets the port the service is running on.
     */
    public int getPort() {
        return port;
    }
    
    private String healthCheck(Request req, Response res) {
        return json(Map.of("status", "healthy", "timestamp", System.currentTimeMillis()));
    }
    
    private String listTopics(Request req, Response res) {
        Set<String> topics = metadataStore.listTopics();
        return json(Map.of("topics", topics, "count", topics.size()));
    }
    
    private String getTopicMetadata(Request req, Response res) {
        String topicName = req.params(":name");
        TopicMetadata metadata = metadataStore.getTopicMetadata(topicName);
        
        if (metadata == null) {
            res.status(404);
            return json(Map.of("error", "Topic not found", "topic", topicName));
        }
        
        return json(metadata);
    }
    
    private String createTopicMetadata(Request req, Response res) {
        try {
            TopicMetadata metadata = objectMapper.readValue(req.body(), TopicMetadata.class);
            
            if (metadataStore.containsTopic(metadata.topicName())) {
                res.status(409);
                return json(Map.of("error", "Topic already exists", "topic", metadata.topicName()));
            }
            
            metadataStore.putTopicMetadata(metadata);
            res.status(201);
            return json(Map.of("message", "Topic metadata created", "topic", metadata.topicName()));
            
        } catch (Exception e) {
            res.status(400);
            return json(Map.of("error", "Invalid request body: " + e.getMessage()));
        }
    }
    
    private String updateTopicMetadata(Request req, Response res) {
        String topicName = req.params(":name");
        
        try {
            TopicMetadata metadata = objectMapper.readValue(req.body(), TopicMetadata.class);
            
            // Ensure topic name in URL matches the metadata
            if (!topicName.equals(metadata.topicName())) {
                res.status(400);
                return json(Map.of("error", "Topic name mismatch"));
            }
            
            metadataStore.putTopicMetadata(metadata);
            return json(Map.of("message", "Topic metadata updated", "topic", topicName));
            
        } catch (Exception e) {
            res.status(400);
            return json(Map.of("error", "Invalid request body: " + e.getMessage()));
        }
    }
    
    private String deleteTopicMetadata(Request req, Response res) {
        String topicName = req.params(":name");
        boolean removed = metadataStore.removeTopicMetadata(topicName);
        
        if (!removed) {
            res.status(404);
            return json(Map.of("error", "Topic not found", "topic", topicName));
        }
        
        return json(Map.of("message", "Topic metadata deleted", "topic", topicName));
    }
    
    private String refreshTopicMetadata(Request req, Response res) {
        String topicName = req.params(":name");
        
        if (!metadataStore.containsTopic(topicName)) {
            res.status(404);
            return json(Map.of("error", "Topic not found", "topic", topicName));
        }
        
        // In a real implementation, this would refresh from external sources
        logger.info("Refreshing metadata for topic: {}", topicName);
        return json(Map.of("message", "Topic metadata refreshed", "topic", topicName));
    }
    
    private String getAllTopicMetadata(Request req, Response res) {
        Map<String, TopicMetadata> allMetadata = metadataStore.getAllTopicMetadata();
        return json(Map.of("topics", allMetadata, "count", allMetadata.size()));
    }
    
    private String clearAllTopics(Request req, Response res) {
        int count = metadataStore.size();
        metadataStore.clear();
        return json(Map.of("message", "All topic metadata cleared", "deleted_count", count));
    }
    
    private String getStats(Request req, Response res) {
        return json(Map.of(
            "total_topics", metadataStore.size(),
            "service_uptime_ms", System.currentTimeMillis() - startTime,
            "port", port,
            "status", "running"
        ));
    }
    
    private void handleException(Exception e, Request req, Response res) {
        logger.error("Unhandled exception in {} {}", req.requestMethod(), req.pathInfo(), e);
        res.status(500);
        res.body(json(Map.of("error", "Internal server error", "message", e.getMessage())));
    }
    
    private String json(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            logger.error("Failed to serialize object to JSON", e);
            return "{\"error\":\"JSON serialization failed\"}";
        }
    }
    
    private static final long startTime = System.currentTimeMillis();
}
