package org.mindignited.kinotic.telemetry.internal.services;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import org.mindignited.kinotic.telemetry.api.config.KinoticTelemetryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages endpoint connections and deploys Vert.x verticles.
 * Responsible for initializing and managing the Vertx instance and deploying the OtelReceiver verticle.
 */
@Component
public class EndpointConnectionManager {
    
    private static final Logger log = LoggerFactory.getLogger(EndpointConnectionManager.class);
    
    private final KinoticTelemetryProperties properties;
    private final ChronicleQueueBuffer buffer;
    private Vertx vertx;
    private String deploymentId;
    private final AtomicReference<CompletableFuture<Void>> shutdownFuture = new AtomicReference<>();
    
    public EndpointConnectionManager(KinoticTelemetryProperties properties, ChronicleQueueBuffer buffer) {
        this.properties = properties;
        this.buffer = buffer;
    }
    
    @PostConstruct
    public void initialize() {
        if (!properties.getReceiver().isEnabled()) {
            log.info("OTel receiver is disabled, skipping endpoint connection manager initialization");
            return;
        }
        
        try {
            // Create Vertx instance
            vertx = Vertx.vertx();
            
            // Create and deploy the OtelReceiver verticle
            OtelReceiver otelReceiver = new OtelReceiver(properties, buffer);
            
            DeploymentOptions deploymentOptions = new DeploymentOptions()
                    .setInstances(1);
            
            vertx.deployVerticle(otelReceiver, deploymentOptions)
                    .onSuccess(id -> {
                        deploymentId = id;
                        log.info("OtelReceiver verticle deployed successfully with deployment ID: {}", id);
                    })
                    .onFailure(error -> {
                        log.error("Failed to deploy OtelReceiver verticle", error);
                        throw new RuntimeException("Failed to deploy OtelReceiver verticle", error);
                    });
            
        } catch (Exception e) {
            log.error("Failed to initialize endpoint connection manager", e);
            throw new RuntimeException("Failed to initialize endpoint connection manager", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        if (vertx == null) {
            return;
        }
        
        CompletableFuture<Void> future = new CompletableFuture<>();
        shutdownFuture.set(future);
        
        try {
            // Undeploy the verticle first
            if (deploymentId != null) {
                vertx.undeploy(deploymentId)
                        .compose(v -> {
                            log.info("OtelReceiver verticle undeployed");
                            // Close Vertx instance
                            return vertx.close();
                        })
                        .onSuccess(v -> {
                            log.info("Vertx instance closed");
                            future.complete(null);
                        })
                        .onFailure(error -> {
                            log.error("Error shutting down endpoint connection manager", error);
                            future.completeExceptionally(error);
                        });
            } else {
                // Just close Vertx if no deployment
                vertx.close()
                        .onSuccess(v -> {
                            log.info("Vertx instance closed");
                            future.complete(null);
                        })
                        .onFailure(error -> {
                            log.error("Error closing Vertx instance", error);
                            future.completeExceptionally(error);
                        });
            }
            
            // Wait for shutdown to complete (with timeout)
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Shutdown timeout or error, forcing closure", e);
            }
            
        } catch (Exception e) {
            log.error("Error during shutdown", e);
            future.completeExceptionally(e);
        }
    }
    
    public Vertx getVertx() {
        return vertx;
    }
    
    public boolean isInitialized() {
        return vertx != null && deploymentId != null;
    }
}
