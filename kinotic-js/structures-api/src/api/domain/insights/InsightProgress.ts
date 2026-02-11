import { DataInsightsComponent } from './DataInsightsComponent'

/**
 * Enumeration of progress update types.
 */
export enum ProgressType {
    STARTED = 'STARTED',           // Analysis has started
    ANALYZING = 'ANALYZING',       // Analyzing the request
    DISCOVERING_DATA = 'DISCOVERING_DATA',  // Discovering relevant structures and data
    GENERATING_CODE = 'GENERATING_CODE',   // Generating web component code
    COMPONENTS_READY = 'COMPONENTS_READY',  // All components have been generated
    COMPLETED = 'COMPLETED',       // All components are ready
    ERROR = 'ERROR'                // An error occurred
}

/**
 * Represents progress updates during data insights generation.
 * This is used with Flux/Observable to provide real-time progress feedback.
 */
export interface InsightProgress {
    
    /**
     * The type of progress update.
     */
    type: ProgressType;
    
    /**
     * Human-readable message describing the current step.
     */
    message: string;
    
    /**
     * When this progress update was created.
     */
    timestamp: string; // ISO date string
    
    /**
     * List of generated components if this is a COMPONENTS_READY update.
     */
    components?: DataInsightsComponent[];
    
    /**
     * Error message if this is an ERROR update.
     */
    errorMessage?: string;
} 