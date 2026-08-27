/**
 * How a step's result was stored in the job scope, recorded on the TaskRecord and governing
 * how the step is rehydrated when a run is resumed.
 */
export enum StoreType {
    /**
     * The step stored nothing. On resume a completed step is skipped.
     */
    NONE = 'NONE',
    /**
     * The step stored transient wiring for later steps in the same run. The value is not
     * persisted; on resume the value is reloaded from its external source of truth.
     */
    RESULT = 'RESULT',
    /**
     * The step stored durable state. The value is serialized into the TaskRecord and on
     * resume is replayed from the record instead of re-executing the step.
     */
    STATE = 'STATE'
}
