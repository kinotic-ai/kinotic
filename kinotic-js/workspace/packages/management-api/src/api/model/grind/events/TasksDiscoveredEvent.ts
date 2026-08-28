import { JobRunEventType } from '@/api/model/grind/events/JobRunEventType'
import type { TaskRecord } from '@/api/model/grind/TaskRecord'

/**
 * The tasks of the run became known: the definition's own tasks at the start of the run, and
 * the subtree a task produced when it generates tasks at runtime.
 */
export class TasksDiscoveredEvent {

    public readonly type: JobRunEventType.TASKS_DISCOVERED = JobRunEventType.TASKS_DISCOVERED

    /**
     * The position of the task the discovered tasks belong under.
     */
    public taskPath: string = ''

    /**
     * The discovered tasks, recorded PENDING, in discovery order.
     */
    public tasks: TaskRecord[] = []

    /**
     * True when the task at taskPath produced the discovery at runtime, false for the
     * definition's own tasks at the start of the run.
     */
    public dynamic: boolean = false

}
