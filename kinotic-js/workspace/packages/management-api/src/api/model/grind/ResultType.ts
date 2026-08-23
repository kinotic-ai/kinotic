/**
 * What kind of value a Result carries in a grind job's result stream.
 */
export enum ResultType {
    /**
     * The step produced its final value. Over the monitoring stream the value is omitted.
     */
    VALUE = 'VALUE',
    /**
     * The task resulted in no action being taken; the value is null.
     */
    NOOP = 'NOOP',
    /**
     * The result value is a Diagnostic message.
     */
    DIAGNOSTIC = 'DIAGNOSTIC',
    /**
     * The result value is a Progress object.
     */
    PROGRESS = 'PROGRESS',
    /**
     * A task returned further steps that will execute under its stepPath. Over the
     * monitoring stream the value is the discovered steps as PENDING TaskRecords,
     * in discovery order.
     */
    DYNAMIC_STEPS = 'DYNAMIC_STEPS',
    /**
     * The result value is a message describing an error that occurred at the given step.
     */
    EXCEPTION = 'EXCEPTION',
    /**
     * The step began executing; the result value is the step description.
     */
    STEP_STARTED = 'STEP_STARTED',
    /**
     * The step finished successfully; the result value is a StepCompletion.
     */
    STEP_COMPLETED = 'STEP_COMPLETED',
    /**
     * The step terminated with a failure; the result value is the failure message.
     */
    STEP_FAILED = 'STEP_FAILED'
}
