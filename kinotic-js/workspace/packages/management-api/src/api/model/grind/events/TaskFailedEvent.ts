import type { JobRunEventType } from '@/api/model/grind/events/JobRunEventType'

/**
 * A task terminated with a failure.
 */
export interface TaskFailedEvent {

    readonly type: JobRunEventType.TASK_FAILED

    /**
     * The task's position in the run's task tree.
     */
    readonly taskPath: string

    /**
     * The failure message.
     */
    readonly error: string

}
