/**
 * The severity of a TaskLogEntry.
 */
export enum TaskLogLevel {
    /**
     * What the task did or found.
     */
    INFO = 'INFO',
    /**
     * Something the task found that needs attention but did not stop it.
     */
    WARN = 'WARN',
    /**
     * Something that went wrong in the task.
     */
    ERROR = 'ERROR'
}
