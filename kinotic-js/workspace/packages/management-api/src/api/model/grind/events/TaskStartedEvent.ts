import { JobRunEventType } from '@/api/model/grind/events/JobRunEventType'

/**
 * A task began executing.
 */
export class TaskStartedEvent {

    public readonly type: JobRunEventType.TASK_STARTED = JobRunEventType.TASK_STARTED

    /**
     * The task's position in the run's task tree.
     */
    public taskPath: string = ''

    /**
     * The task's description.
     */
    public description: string | null = null

}
