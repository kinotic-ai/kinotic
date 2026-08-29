/**
 * How a task's result was stored in the job scope, recorded on the TaskRecord and governing
 * how the task is rehydrated when a run is resumed.
 */
export enum StoreType {
    /**
     * The task stored nothing. On resume a completed task is skipped.
     */
    NONE = 'NONE',
    /**
     * The task stored transient wiring for later tasks in the same run. The value is not
     * persisted; on resume the value is reloaded from its external source of truth.
     */
    RESULT = 'RESULT',
    /**
     * The task stored durable state. The value is serialized into the TaskRecord and on
     * resume is replayed from the record instead of re-executing the task.
     */
    STATE = 'STATE'
}
