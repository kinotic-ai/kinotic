import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy, Page, Pageable } from '@kinotic-ai/core'
import type { Observable } from 'rxjs'
import type { JobRun } from '@/api/model/grind/JobRun'
import type { TaskRecord } from '@/api/model/grind/TaskRecord'
import type { JobRunEvent } from '@/api/model/grind/events/JobRunEvent'

/**
 * Read access to grind job runs for the authenticated participant: an organization or
 * application participant sees the runs its organization owns, a system participant sees
 * every run. A run's TaskRecords are its task ledger - every discovered task has a record,
 * PENDING until it starts executing.
 */
export interface IJobMonitoringService {

    /**
     * Finds the job runs the participant may view.
     * @param pageable the page of runs to return
     */
    findJobRuns(pageable: Pageable): Promise<Page<JobRun>>

    /**
     * Finds a single job run the participant may view.
     * @param jobRunId the id of the run
     */
    findJobRun(jobRunId: string): Promise<JobRun>

    /**
     * Finds the task ledger of a job run the participant may view.
     * @param jobRunId the id of the run
     * @param pageable the page of records to return
     */
    findTasks(jobRunId: string, pageable: Pageable): Promise<Page<TaskRecord>>

    /**
     * Opens a live view of a job run the participant may view, replaying every JobRunEvent
     * emitted since the run started and continuing until the run terminates. Completes
     * without emissions when the run is not currently executing.
     * @param jobRunId the id of the run to watch
     */
    watch(jobRunId: string): Observable<JobRunEvent>
}

export class JobMonitoringService implements IJobMonitoringService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.grind.api.services.JobMonitoringService`)
    }

    public findJobRuns(pageable: Pageable): Promise<Page<JobRun>> {
        return this.serviceProxy.invoke('findJobRuns', [pageable])
    }

    public findJobRun(jobRunId: string): Promise<JobRun> {
        return this.serviceProxy.invoke('findJobRun', [jobRunId])
    }

    public findTasks(jobRunId: string, pageable: Pageable): Promise<Page<TaskRecord>> {
        return this.serviceProxy.invoke('findTasks', [jobRunId, pageable])
    }

    public watch(jobRunId: string): Observable<JobRunEvent> {
        return this.serviceProxy.invokeStream('watch', [jobRunId])
    }
}
