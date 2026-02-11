package org.mindignited.kinotic.telemetry.api.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Represents a batch of telemetry data items.
 */
@Getter
@RequiredArgsConstructor
public class TelemetryBatch {
    
    private final TelemetryData.TelemetryType type;
    private final List<TelemetryData> items;
    private final long timestamp;
    
    public TelemetryBatch(TelemetryData.TelemetryType type, List<TelemetryData> items) {
        this.type = type;
        this.items = items;
        this.timestamp = System.currentTimeMillis();
    }
    
    public int size() {
        return items != null ? items.size() : 0;
    }
    
    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }
}

