import { JobRunEventType } from '@/api/model/grind/events/JobRunEventType'

/**
 * A running task reported its progress. Emitted between the task's TaskStartedEvent and its
 * terminal event, as often as the task reports.
 */
export class TaskProgressEvent {

    public readonly type: JobRunEventType.TASK_PROGRESS = JobRunEventType.TASK_PROGRESS

    /**
     * The task's position in the run's task tree.
     */
    public taskPath: string = ''

    /**
     * How close the task is to completion, 0 to 100.
     */
    public percentageComplete: number = 0

    /**
     * What the task is currently doing, or null.
     */
    public message: string | null = null

}
