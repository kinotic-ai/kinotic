import type { JobRunEventType } from '@/api/model/grind/events/JobRunEventType'
import type { StoreType } from '@/api/model/grind/StoreType'

/**
 * A task finished successfully, describing what it stored in the job scope. The stored object
 * itself stays in the executing process; only a task whose store declared wire publication
 * carries its value here.
 */
export interface TaskCompletedEvent {

    readonly type: JobRunEventType.TASK_COMPLETED

    /**
     * The task's position in the run's task tree.
     */
    readonly taskPath: string

    /**
     * How the task stored its result, NONE if it stored nothing.
     */
    readonly storeType: StoreType

    /**
     * The name the result was stored under, or null if nothing was stored.
     */
    readonly storedName: string | null

    /**
     * The stored value as JSON, or null unless the task's store declared wire publication.
     */
    readonly wireValue: any

}
