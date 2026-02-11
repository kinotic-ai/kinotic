package org.kinotic.structures.api.domain.insights;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Represents the result of analyzing a user's request to determine their intent.
 * This helps the system decide whether to create a new visualization or modify an existing one.
 */
@Accessors(chain = true)
@Data
@NoArgsConstructor
public class RequestAnalysisResult {
    
    /**
     * The type of request the user is making.
     */
    private RequestType requestType;
    
    /**
     * The name/ID of the visualization to modify (only relevant for MODIFY requests).
     */
    private String visualizationName;
    
    /**
     * Additional context extracted from the user's request.
     */
    private String additionalContext;
    
    /**
     * The types of requests that can be made.
     */
    public enum RequestType {
        /**
         * User wants to create a new visualization.
         */
        NEW_VISUALIZATION,
        
        /**
         * User wants to modify an existing visualization.
         */
        MODIFY_EXISTING
    }
} 