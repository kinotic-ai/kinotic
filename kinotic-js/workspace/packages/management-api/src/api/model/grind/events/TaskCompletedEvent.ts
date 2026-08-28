import { JobRunEventType } from '@/api/model/grind/events/JobRunEventType'
import { StoreType } from '@/api/model/grind/StoreType'

/**
 * A task finished successfully, describing what it stored in the job scope. The stored object
 * itself stays in the executing process; only a task whose store declared wire publication
 * carries its value here.
 */
export class TaskCompletedEvent {

    public readonly type: JobRunEventType.TASK_COMPLETED = JobRunEventType.TASK_COMPLETED

    /**
     * The task's position in the run's task tree.
     */
    public readonly taskPath: string = ''

    /**
     * How the task stored its result, NONE if it stored nothing.
     */
    public readonly storeType: StoreType = StoreType.NONE

    /**
     * The name the result was stored under, or null if nothing was stored.
     */
    public readonly storedName: string | null = null

    /**
     * The stored value as JSON, or null unless the task's store declared wire publication.
     */
    public readonly wireValue: any = null

}
