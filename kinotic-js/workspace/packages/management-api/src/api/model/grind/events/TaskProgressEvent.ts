import type { JobRunEventType } from '@/api/model/grind/events/JobRunEventType'
import type { TaskView } from '@/api/model/grind/TaskView'

/**
 * A running task reported its progress, or replaced the view it shows. Emitted between the task's
 * TaskStartedEvent and its terminal event, as often as the task reports.
 */
export interface TaskProgressEvent {

    readonly type: JobRunEventType.TASK_PROGRESS

    /**
     * The task's position in the run's task tree.
     */
    readonly taskPath: string

    /**
     * How close the task is to completion, 0 to 100, or null for a report that only replaces the
     * view.
     */
    readonly percentageComplete: number | null

    /**
     * What the task is currently doing, or null.
     */
    readonly message: string | null

    /**
     * The view the task now shows, or null when the report leaves it unchanged.
     */
    readonly view: TaskView | null

}
