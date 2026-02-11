package org.mindignited.kinotic.telemetry.internal.services;

import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.grpcio.server.GrpcIoServer;
import io.vertx.grpcio.server.GrpcIoServiceBridge;
import org.mindignited.kinotic.telemetry.api.config.KinoticTelemetryProperties;
import org.mindignited.kinotic.telemetry.api.domain.TelemetryData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vert.x gRPC-based receiver for OTLP data.
 * Handles OTLP gRPC endpoints for traces and metrics using Vert.x gRPC/IO Server.
 */
public class OtelReceiver extends VerticleBase {
    
    private static final Logger log = LoggerFactory.getLogger(OtelReceiver.class);
    
    private final KinoticTelemetryProperties.Receiver config;
    private final ChronicleQueueBuffer buffer;
    private HttpServer httpServer;
    private GrpcIoServer grpcServer;
    
    public OtelReceiver(KinoticTelemetryProperties properties, ChronicleQueueBuffer buffer) {
        this.config = properties.getReceiver();
        this.buffer = buffer;
    }
    
    @Override
    public Future<?> start() {
        if (!config.isEnabled()) {
            log.info("OTel receiver is disabled");
            return Future.succeededFuture();
        }
        
        try {
            // Create Vert.x gRPC/IO server
            grpcServer = GrpcIoServer.server(vertx);

            // Create trace service implementation
            TraceServiceGrpc.TraceServiceImplBase traceService = new TraceServiceGrpc.TraceServiceImplBase() {
                @Override
                public void export(ExportTraceServiceRequest request, StreamObserver<ExportTraceServiceResponse> responseObserver) {
                    try {
                        if (request == null || request.getResourceSpansCount() == 0) {
                            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                                    .withDescription("Empty request")
                                    .asException());
                            return;
                        }
                        
                        // Extract and store each ResourceSpans individually
                        int totalBytes = 0;
                        for (var resourceSpans : request.getResourceSpansList()) {
                            byte[] data = resourceSpans.toByteArray();
                            TelemetryData telemetryData = TelemetryData.traces(data);
                            buffer.write(telemetryData);
                            totalBytes += data.length;
                        }

                        // Send success response
                        ExportTraceServiceResponse response = ExportTraceServiceResponse.newBuilder().build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                        
                        log.debug("Received {} ResourceSpans ({} total bytes) of trace data via gRPC", 
                                request.getResourceSpansCount(), totalBytes);
                    } catch (Exception e) {
                        log.error("Error handling traces", e);
                        responseObserver.onError(io.grpc.Status.INTERNAL
                                .withDescription("Internal server error: " + e.getMessage())
                                .withCause(e)
                                .asException());
                    }
                }
            };
            
            // Create metrics service implementation
            MetricsServiceGrpc.MetricsServiceImplBase metricsService = new MetricsServiceGrpc.MetricsServiceImplBase() {
                @Override
                public void export(ExportMetricsServiceRequest request, StreamObserver<ExportMetricsServiceResponse> responseObserver) {
                    try {
                        if (request == null || request.getResourceMetricsCount() == 0) {
                            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                                    .withDescription("Empty request")
                                    .asException());
                            return;
                        }
                        
                        // Extract and store each ResourceMetrics individually
                        int totalBytes = 0;
                        for (var resourceMetrics : request.getResourceMetricsList()) {
                            byte[] data = resourceMetrics.toByteArray();
                            TelemetryData telemetryData = TelemetryData.metrics(data);
                            buffer.write(telemetryData);
                            totalBytes += data.length;
                        }
                        
                        // Send success response
                        ExportMetricsServiceResponse response = ExportMetricsServiceResponse.newBuilder().build();
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                        
                        log.debug("Received {} ResourceMetrics ({} total bytes) of metrics data via gRPC", 
                                request.getResourceMetricsCount(), totalBytes);
                    } catch (Exception e) {
                        log.error("Error handling metrics", e);
                        responseObserver.onError(io.grpc.Status.INTERNAL
                                .withDescription("Internal server error: " + e.getMessage())
                                .withCause(e)
                                .asException());
                    }
                }
            };
            
            // Bridge the services to the gRPC server
            GrpcIoServiceBridge traceBridge = GrpcIoServiceBridge.bridge(traceService);
            traceBridge.bind(grpcServer);
            
            GrpcIoServiceBridge metricsBridge = GrpcIoServiceBridge.bridge(metricsService);
            metricsBridge.bind(grpcServer);
            
            // Create HTTP/2 server to host the gRPC server
            httpServer = vertx.createHttpServer();
            return httpServer.requestHandler(grpcServer)
                    .listen(config.getPort(), config.getHost())
                    .onSuccess(server -> {
                        log.info("OTel gRPC receiver started on {}:{}", config.getHost(), config.getPort());
                    })
                    .onFailure(error -> {
                        log.error("Failed to start OTel receiver", error);
                    });
            
        } catch (Exception e) {
            log.error("Failed to initialize OTel receiver", e);
            return Future.failedFuture(e);
        }
    }
    
    @Override
    public Future<?> stop() {
        try {
            if (httpServer != null) {
                return httpServer.close()
                        .onSuccess(v -> {
                            log.info("HTTP server stopped");
                            log.info("OTel gRPC receiver stopped");
                        })
                        .onFailure(error -> {
                            log.error("Error stopping HTTP server", error);
                        });
            } else {
                log.info("OTel gRPC receiver stopped");
                return Future.succeededFuture();
            }
        } catch (Exception e) {
            log.error("Error shutting down OTel receiver", e);
            return Future.failedFuture(e);
        }
    }
}
