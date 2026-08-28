import { JobRunEventType } from '@/api/model/grind/events/JobRunEventType'

/**
 * A task terminated with a failure.
 */
export class TaskFailedEvent {

    public readonly type: JobRunEventType.TASK_FAILED = JobRunEventType.TASK_FAILED

    /**
     * The task's position in the run's task tree.
     */
    public taskPath: string = ''

    /**
     * The failure message.
     */
    public error: string = ''

}
