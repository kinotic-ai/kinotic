package org.kinotic.persistence.api.model.insights;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.kinotic.persistence.api.model.EntityDefinition;

/**
 * Represents a request for AI-powered data analysis and visualization generation.
 * 
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsightRequest {
    
    /**
     * The user's natural language query about their data.
     * Examples:
     * - "Show me my products grouped by category"
     * - "Give me a chart of customer orders over time"
     * - "Display provider qualifications and specialties"
     */
    private String query;
    
    /**
     * The application ID to search for {@link EntityDefinition}s within.
     * All analysis will be limited to {@link EntityDefinition}s in this application.
     */
    private String applicationId;
    
    /**
     * Optional: Specific {@link EntityDefinition} ID to focus the analysis on.
     * If provided, will prioritize this {@link EntityDefinition} in the analysis.
     * Format: "applicationId.entityDefinitionName"
     */
    private String focusEntityDefinitionId;
    
    /**
     * Optional: Preferred visualization type if user has a preference.
     * Examples: "bar", "pie", "line", "scatter", "table"
     */
    private String preferredVisualization;
    
    /**
     * Optional: Additional context or constraints for the analysis.
     * Examples: "only show last 30 days", "include only active records"
     */
    private String additionalContext;
}