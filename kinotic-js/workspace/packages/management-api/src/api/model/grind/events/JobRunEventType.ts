/**
 * Which event of a job run's stream a JobRunEvent is; the discriminator carried on the wire.
 */
export enum JobRunEventType {
    TASKS_DISCOVERED = 'tasksDiscovered',
    TASK_STARTED = 'taskStarted',
    TASK_PROGRESS = 'taskProgress',
    TASK_COMPLETED = 'taskCompleted',
    TASK_FAILED = 'taskFailed'
}
