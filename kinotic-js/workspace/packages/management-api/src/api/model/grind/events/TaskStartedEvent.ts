import type { JobRunEventType } from '@/api/model/grind/events/JobRunEventType'
import type { TaskView } from '@/api/model/grind/TaskView'

/**
 * A task began executing.
 */
export interface TaskStartedEvent {

    readonly type: JobRunEventType.TASK_STARTED

    /**
     * The task's position in the run's task tree.
     */
    readonly taskPath: string

    /**
     * The task's description.
     */
    readonly description: string | null

    /**
     * The view the task shows from its start, or null when it declares none.
     */
    readonly view: TaskView | null

}
