package com.example.kafka.discovery.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone server application for the Kafka broker discovery service.
 * Can be run independently to provide broker discovery REST APIs.
 */
public class DiscoveryServerApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryServerApplication.class);
    
    private static final int DEFAULT_PORT = 8080;
    
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        
        // Parse command line arguments
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.error("Invalid port number: {}. Using default port {}", args[0], DEFAULT_PORT);
                port = DEFAULT_PORT;
            }
        }
        
        logger.info("=== Kafka Broker Discovery Server ===");
        logger.info("Starting server on port: {}", port);
        
        BrokerDiscoveryService service = null;
        
        try {
            service = new BrokerDiscoveryService(port);
            
            // Add shutdown hook for graceful shutdown
            BrokerDiscoveryService finalService = service;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, stopping server...");
                if (finalService != null) {
                    finalService.stop();
                }
                logger.info("Server stopped gracefully");
            }));
            
            service.start();
            
            logger.info("Discovery server started successfully");
            logger.info("Available endpoints:");
            logger.info("  Health: GET http://localhost:{}/health", port);
            logger.info("  List topics: GET http://localhost:{}/api/topics", port);
            logger.info("  Get topic: GET http://localhost:{}/api/topics/:name", port);
            logger.info("  Admin stats: GET http://localhost:{}/api/admin/stats", port);
            logger.info("Press Ctrl+C to stop the server");
            
            // Keep the main thread alive
            Thread.currentThread().join();
            
        } catch (InterruptedException e) {
            logger.info("Server interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Failed to start discovery server", e);
            System.exit(1);
        } finally {
            if (service != null) {
                service.stop();
            }
        }
    }
}
