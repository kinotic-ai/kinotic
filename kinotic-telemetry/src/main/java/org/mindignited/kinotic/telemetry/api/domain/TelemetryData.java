package org.mindignited.kinotic.telemetry.api.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents telemetry data that can be either traces or metrics.
 */
@Getter
@RequiredArgsConstructor
public class TelemetryData {
    
    private final TelemetryType type;
    private final byte[] data; // Serialized OTLP data (protobuf)
    private final long timestamp;
    
    public enum TelemetryType {
        TRACES,
        METRICS
    }
    
    public static TelemetryData traces(byte[] data) {
        return new TelemetryData(TelemetryType.TRACES, data, System.currentTimeMillis());
    }
    
    public static TelemetryData metrics(byte[] data) {
        return new TelemetryData(TelemetryType.METRICS, data, System.currentTimeMillis());
    }
}

