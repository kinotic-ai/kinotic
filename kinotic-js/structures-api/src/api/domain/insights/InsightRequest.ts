/**
 * Represents a request for AI-powered data analysis and visualization generation.
 */
export interface InsightRequest {
    
    /**
     * The user's natural language query about their data.
     * Examples:
     * - "Show me my products grouped by category"
     * - "Give me a chart of customer orders over time"
     * - "Display provider qualifications and specialties"
     */
    query: string;
    
    /**
     * The application ID to search for structures within.
     * All analysis will be limited to structures in this application.
     */
    applicationId: string;
    
    /**
     * Optional: Specific structure ID to focus the analysis on.
     * If provided, will prioritize this structure in the analysis.
     * Format: "applicationId.structureName"
     */
    focusStructureId?: string;
    
    /**
     * Optional: Preferred visualization type if user has a preference.
     * Examples: "bar", "pie", "line", "scatter", "table"
     */
    preferredVisualization?: string;
    
    /**
     * Optional: Additional context or constraints for the analysis.
     * Examples: "only show last 30 days", "include only active records"
     */
    additionalContext?: string;
}