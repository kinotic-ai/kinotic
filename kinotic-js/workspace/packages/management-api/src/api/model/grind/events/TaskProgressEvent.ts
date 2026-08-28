import type { JobRunEventType } from '@/api/model/grind/events/JobRunEventType'

/**
 * A running task reported its progress. Emitted between the task's TaskStartedEvent and its
 * terminal event, as often as the task reports.
 */
export interface TaskProgressEvent {

    readonly type: JobRunEventType.TASK_PROGRESS

    /**
     * The task's position in the run's task tree.
     */
    readonly taskPath: string

    /**
     * How close the task is to completion, 0 to 100.
     */
    readonly percentageComplete: number

    /**
     * What the task is currently doing, or null.
     */
    readonly message: string | null

}
