import { ResultType } from '@/api/model/grind/ResultType'
import { StepInfo } from '@/api/model/grind/StepInfo'

/**
 * One emission from a grind job's result stream.
 */
export class Result {

    /**
     * Locates the step responsible for creating this result within the run's step tree.
     */
    public stepInfo!: StepInfo

    /**
     * What kind of value this result carries - see each ResultType member for the value's
     * shape over the monitoring stream.
     */
    public resultType!: ResultType

    /**
     * The value, shaped by resultType: a Progress for PROGRESS, a StepRecord array for
     * DYNAMIC_STEPS, a StepCompletion for STEP_COMPLETED, a message string for
     * STEP_STARTED, STEP_FAILED, and EXCEPTION, a Diagnostic for DIAGNOSTIC, and null
     * for VALUE and NOOP.
     */
    public value: any = null

}
