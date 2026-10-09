import type { TaskLogLevel } from '@/api/model/grind/TaskLogLevel'

/**
 * One line a task wrote to its log.
 */
export interface TaskLogEntry {

    /**
     * When the task wrote the line, as an ISO-8601 timestamp.
     */
    readonly timestamp: string

    /**
     * The line's severity.
     */
    readonly level: TaskLogLevel

    /**
     * The text of the line.
     */
    readonly message: string

}
