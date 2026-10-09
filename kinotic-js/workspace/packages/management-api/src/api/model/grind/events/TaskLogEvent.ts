import type { JobRunEventType } from '@/api/model/grind/events/JobRunEventType'
import type { TaskLogEntry } from '@/api/model/grind/TaskLogEntry'

/**
 * A running task wrote a line to its log. Emitted between the task's TaskStartedEvent and its
 * terminal event, once per line.
 */
export interface TaskLogEvent {

    readonly type: JobRunEventType.TASK_LOG

    /**
     * The task's position in the run's task tree.
     */
    readonly taskPath: string

    /**
     * The line the task wrote.
     */
    readonly entry: TaskLogEntry

}
