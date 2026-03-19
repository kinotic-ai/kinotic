package org.mindignited.kinotic.telemetry.internal.services.output.otlp;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpcio.client.GrpcIoClient;
import io.vertx.grpcio.client.GrpcIoClientChannel;
import org.mindignited.kinotic.telemetry.api.config.KinoticTelemetryProperties;
import org.mindignited.kinotic.telemetry.api.domain.TelemetryData;
import org.mindignited.kinotic.telemetry.internal.services.output.OutputProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.protobuf.InvalidProtocolBufferException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OTLP gRPC output processor that sends telemetry data to an OTLP gRPC endpoint using Vert.x gRPC client.
 */
@Component
public class OtlpGrpcOutput implements OutputProcessor {
    
    private static final Logger log = LoggerFactory.getLogger(OtlpGrpcOutput.class);
    
    private final KinoticTelemetryProperties.Output config;
    private final Vertx vertx;
    private GrpcIoClient grpcClient;
    private GrpcIoClientChannel channel;
    private TraceServiceGrpc.TraceServiceStub traceStub;
    private MetricsServiceGrpc.MetricsServiceStub metricsStub;
    private SocketAddress serverAddress;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    
    public OtlpGrpcOutput(KinoticTelemetryProperties properties, Vertx vertx) {
        this.config = properties.getOutput();
        this.vertx = vertx;
        this.enabled.set(config.isEnabled());
    }
    
    @PostConstruct
    @Override
    public void initialize() throws Exception {
        if (!config.isEnabled()) {
            log.info("OTLP gRPC output is disabled");
            return;
        }
        
        if (initialized.compareAndSet(false, true)) {
            try {
                // Create Vert.x gRPC client using the injected Vertx bean
                grpcClient = GrpcIoClient.client(vertx);
                
                // Parse endpoint URI
                URI endpointUri = URI.create(config.getEndpoint());
                String host = endpointUri.getHost();
                int port = endpointUri.getPort() > 0 ? endpointUri.getPort() : 4317;
                
                // Create socket address
                serverAddress = SocketAddress.inetSocketAddress(port, host);
                
                // Create gRPC channel
                channel = new GrpcIoClientChannel(grpcClient, serverAddress);
                
                // Create async stubs (non-blocking)
                traceStub = TraceServiceGrpc.newStub(channel);
                metricsStub = MetricsServiceGrpc.newStub(channel);
                
                log.info("OTLP gRPC output initialized: {}:{}", host, port);
            } catch (Exception e) {
                log.error("Failed to initialize OTLP gRPC output", e);
                initialized.set(false);
                throw e;
            }
        }
    }
    
    @PreDestroy
    @Override
    public void shutdown() {
        if (initialized.get()) {
            try {
                if (grpcClient != null) {
                    grpcClient.close()
                            .onSuccess(v -> log.info("OTLP gRPC client closed"))
                            .onFailure(error -> log.error("Error closing gRPC client", error));
                }
                // Wait a bit for graceful shutdown
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                log.info("OTLP gRPC output shut down");
            } catch (Exception e) {
                log.error("Error shutting down OTLP gRPC output", e);
            } finally {
                initialized.set(false);
            }
        }
    }
    
    @Override
    public String getType() {
        return "otlp-grpc";
    }
    
    @Override
    public CompletableFuture<Void> process(List<TelemetryData> data) {
        if (!isEnabled() || !isReady()) {
            return CompletableFuture.completedFuture(null);
        }
        
        // Group data by type
        List<TelemetryData> traces = data.stream()
                .filter(d -> d.getType() == TelemetryData.TelemetryType.TRACES)
                .toList();
        
        List<TelemetryData> metrics = data.stream()
                .filter(d -> d.getType() == TelemetryData.TelemetryType.METRICS)
                .toList();
        
        // Process both traces and metrics in parallel using Futures
        Future<Void> tracesFuture = traces.isEmpty() 
                ? Future.succeededFuture() 
                : processTracesAsync(traces);
        
        Future<Void> metricsFuture = metrics.isEmpty() 
                ? Future.succeededFuture() 
                : processMetricsAsync(metrics);
        
        // Combine both futures
        Future<Void> combinedFuture = tracesFuture.compose(v -> metricsFuture);
        return combinedFuture.toCompletionStage()
                .thenApply(v -> (Void) null)
                .toCompletableFuture()
                .exceptionally(error -> {
                    log.error("Error processing telemetry data", error);
                    return null;
                });
    }
    
    private Future<Void> processTracesAsync(List<TelemetryData> traces) {
        if (traceStub == null) {
            return Future.succeededFuture();
        }
        
        return processTracesWithRetry(traces, 0);
    }
    
    private Future<Void> processTracesWithRetry(List<TelemetryData> traces, int retryCount) {
        try {
            // Parse protobuf data and create export request
            ExportTraceServiceRequest.Builder requestBuilder = ExportTraceServiceRequest.newBuilder();
            
            for (TelemetryData data : traces) {
                try {
                    ResourceSpans resourceSpans = ResourceSpans.parseFrom(data.getData());
                    requestBuilder.addResourceSpans(resourceSpans);
                } catch (InvalidProtocolBufferException e) {
                    log.warn("Failed to parse trace data, skipping", e);
                }
            }
            
            if (requestBuilder.getResourceSpansCount() == 0) {
                return Future.succeededFuture();
            }
            
            ExportTraceServiceRequest request = requestBuilder.build();
            Promise<ExportTraceServiceResponse> promise = Promise.promise();
            
            traceStub.export(request, new StreamObserver<ExportTraceServiceResponse>() {
                @Override
                public void onNext(ExportTraceServiceResponse value) {
                    promise.complete(value);
                }
                
                @Override
                public void onError(Throwable t) {
                    promise.fail(t);
                }
                
                @Override
                public void onCompleted() {
                    if (!promise.future().isComplete()) {
                        promise.complete();
                    }
                }
            });
            
            Future<ExportTraceServiceResponse> responseFuture = promise.future();
            return responseFuture.<Void>map(v -> {
                        log.debug("Exported {} traces via OTLP gRPC", request.getResourceSpansCount());
                        return null;
                    })
                    .recover(error -> {
                        Status status = null;
                        if (error instanceof StatusException) {
                            status = ((StatusException) error).getStatus();
                        }
                        
                        if (status != null && 
                            (status.getCode() == Status.Code.UNAVAILABLE || 
                             status.getCode() == Status.Code.DEADLINE_EXCEEDED)) {
                            if (retryCount < config.getMaxRetries()) {
                                log.warn("Failed to export traces, retrying ({}/{})", retryCount + 1, config.getMaxRetries());
                                // Wait before retry using Vertx timer
                                Promise<Void> delayPromise = Promise.promise();
                                vertx.setTimer(TimeUnit.MILLISECONDS.toMillis(config.getRetryDelayMs()), id -> {
                                    delayPromise.complete();
                                });
                                return delayPromise.future()
                                        .<Void>compose(v -> processTracesWithRetry(traces, retryCount + 1));
                            } else {
                                log.error("Failed to export traces after {} retries", config.getMaxRetries(), error);
                                return Future.<Void>failedFuture(error);
                            }
                        } else {
                            log.error("Failed to export traces", error);
                            return Future.<Void>failedFuture(error);
                        }
                    });
        } catch (Exception e) {
            log.error("Unexpected error exporting traces", e);
            return Future.failedFuture(e);
        }
    }
    
    private Future<Void> processMetricsAsync(List<TelemetryData> metrics) {
        if (metricsStub == null) {
            return Future.succeededFuture();
        }
        
        return processMetricsWithRetry(metrics, 0);
    }
    
    private Future<Void> processMetricsWithRetry(List<TelemetryData> metrics, int retryCount) {
        try {
            // Parse protobuf data and create export request
            ExportMetricsServiceRequest.Builder requestBuilder = ExportMetricsServiceRequest.newBuilder();
            
            for (TelemetryData data : metrics) {
                try {
                    ResourceMetrics resourceMetrics = ResourceMetrics.parseFrom(data.getData());
                    requestBuilder.addResourceMetrics(resourceMetrics);
                } catch (InvalidProtocolBufferException e) {
                    log.warn("Failed to parse metrics data, skipping", e);
                }
            }
            
            if (requestBuilder.getResourceMetricsCount() == 0) {
                return Future.succeededFuture();
            }
            
            ExportMetricsServiceRequest request = requestBuilder.build();
            Promise<ExportMetricsServiceResponse> promise = Promise.promise();
            
            metricsStub.export(request, new StreamObserver<ExportMetricsServiceResponse>() {
                @Override
                public void onNext(ExportMetricsServiceResponse value) {
                    promise.complete(value);
                }
                
                @Override
                public void onError(Throwable t) {
                    promise.fail(t);
                }
                
                @Override
                public void onCompleted() {
                    if (!promise.future().isComplete()) {
                        promise.complete();
                    }
                }
            });
            
            Future<ExportMetricsServiceResponse> responseFuture = promise.future();
            return responseFuture.<Void>map(v -> {
                        log.debug("Exported {} metrics via OTLP gRPC", request.getResourceMetricsCount());
                        return null;
                    })
                    .recover(error -> {
                        Status status = null;
                        if (error instanceof StatusException) {
                            status = ((StatusException) error).getStatus();
                        }
                        
                        if (status != null && 
                            (status.getCode() == Status.Code.UNAVAILABLE || 
                             status.getCode() == Status.Code.DEADLINE_EXCEEDED)) {
                            if (retryCount < config.getMaxRetries()) {
                                log.warn("Failed to export metrics, retrying ({}/{})", retryCount + 1, config.getMaxRetries());
                                // Wait before retry using Vertx timer
                                Promise<Void> delayPromise = Promise.promise();
                                vertx.setTimer(TimeUnit.MILLISECONDS.toMillis(config.getRetryDelayMs()), id -> {
                                    delayPromise.complete();
                                });
                                return delayPromise.future()
                                        .<Void>compose(v -> processMetricsWithRetry(metrics, retryCount + 1));
                            } else {
                                log.error("Failed to export metrics after {} retries", config.getMaxRetries(), error);
                                return Future.<Void>failedFuture(error);
                            }
                        } else {
                            log.error("Failed to export metrics", error);
                            return Future.<Void>failedFuture(error);
                        }
                    });
        } catch (Exception e) {
            log.error("Unexpected error exporting metrics", e);
            return Future.failedFuture(e);
        }
    }
    
    @Override
    public boolean isEnabled() {
        return enabled.get() && config.isEnabled();
    }
    
    @Override
    public boolean isReady() {
        return initialized.get() && grpcClient != null && channel != null;
    }
}

