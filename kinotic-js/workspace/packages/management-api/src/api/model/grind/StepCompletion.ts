import { StoreType } from '@/api/model/grind/StoreType'

/**
 * The value of a STEP_COMPLETED Result, describing what the completed step stored in the
 * job scope when it finished.
 */
export class StepCompletion {

    /**
     * How the step stored its result, NONE if it stored nothing.
     */
    public storeType: StoreType = StoreType.NONE

    /**
     * The name the step's result was stored under, or null if the step stored nothing
     * or stored under generated names.
     */
    public storedName: string | null = null

    /**
     * The stored value. Always null over the monitoring stream - the produced object stays
     * in the executing process.
     */
    public storedValue: any = null

}
