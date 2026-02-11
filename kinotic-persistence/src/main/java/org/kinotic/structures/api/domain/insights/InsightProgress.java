package org.kinotic.structures.api.domain.insights;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * Represents progress updates during data insights generation.
 * This is used with Flux/Observable to provide real-time progress feedback.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsightProgress {
    
    /**
     * The type of progress update.
     */
    private ProgressType type;
    
    /**
     * Human-readable message describing the current step.
     */
    private String message;
    
    /**
     * When this progress update was created.
     */
    private Instant timestamp;
    
    /**
     * List of generated components if this is a COMPONENTS_READY update.
     */
    private List<DataInsightsComponent> components;
    
    /**
     * Error message if this is an ERROR update.
     */
    private String errorMessage;
    
    /**
     * Enumeration of progress update types.
     */
    public enum ProgressType {
        STARTED,           // Analysis has started
        ANALYZING,         // Analyzing the request
        DISCOVERING_DATA,  // Discovering relevant structures and data
        GENERATING_CODE,   // Generating web component code
        COMPONENTS_READY,  // All components have been generated
        COMPLETED,         // All components are ready
        ERROR             // An error occurred
    }
} 