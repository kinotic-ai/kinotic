import type { DataInsightsComponent } from './DataInsightsComponent'

/**
 * Enumeration of progress update types.
 */
export enum ProgressType {
    STARTED = 'STARTED',
    ANALYZING = 'ANALYZING',
    DISCOVERING_DATA = 'DISCOVERING_DATA',
    GENERATING_CODE = 'GENERATING_CODE',
    COMPONENTS_READY = 'COMPONENTS_READY',
    COMPLETED = 'COMPLETED',
    ERROR = 'ERROR'
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
