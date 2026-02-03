package org.mindignited.structures.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;

/**
 * Utility class for checking container health status
 */
public class ContainerHealthChecker {
    
    private static final Logger log = LoggerFactory.getLogger(ContainerHealthChecker.class);
    
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    
    /**
     * Check if Elasticsearch is healthy and ready
     */
    public static boolean isElasticsearchHealthy(String host, int port) {
        String healthUrl = String.format("http://%s:%d/_cluster/health", host, port);
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(healthUrl))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String body = response.body();
                // Check if cluster is in a healthy state (green or yellow)
                return body.contains("\"status\":\"green\"") || 
                       body.contains("\"status\":\"yellow\"");
            }
            
            log.debug("Elasticsearch health check returned status: {}", response.statusCode());
            return false;
            
        } catch (Exception e) {
            log.debug("Elasticsearch health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if Keycloak is healthy and ready
     */
public static boolean isKeycloakHealthy(String host, int port) {
        String healthUrl = String.format("http://%s:%d/health/ready", host, port);
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(healthUrl))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            boolean isHealthy = response.statusCode() == 200;
            if (isHealthy) {
                log.debug("Keycloak health check successful");
            } else {
                log.debug("Keycloak health check returned status: {}", response.statusCode());
            }
            
            return isHealthy;
            
        } catch (Exception e) {
            log.debug("Keycloak health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Wait for a container to become healthy with retry logic
     */
    public static boolean waitForContainerHealth(
            String containerName,
            HealthCheckFunction healthCheck,
            int maxAttempts,
            long delayMs) {
        
        log.info("Waiting for {} to become healthy...", containerName);
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (healthCheck.check()) {
                log.info("{} is healthy after {} attempts", containerName, attempt);
                return true;
            }
            
            if (attempt < maxAttempts) {
                log.debug("{} health check attempt {} failed, retrying in {} ms...", 
                    containerName, attempt, delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while waiting for {} to become healthy", containerName);
                    return false;
                }
            }
        }
        
        log.error("{} failed to become healthy after {} attempts", containerName, maxAttempts);
        return false;
    }
    
    /**
     * Functional interface for health checks
     */
    @FunctionalInterface
    public interface HealthCheckFunction {
        boolean check();
    }
}
