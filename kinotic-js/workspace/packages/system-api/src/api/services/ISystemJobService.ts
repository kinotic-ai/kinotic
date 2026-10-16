import { SYSTEM_API_ZONE, type JobRun } from '@kinotic-ai/management-api'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { SystemJobDescriptor } from '@/api/model/SystemJobDescriptor'

/**
 * Starts the platform's system jobs on demand. A run is owned by the platform and executes on the
 * node that started it, and is listed, watched and read through Kinotic.jobMonitoring like any
 * other job run.
 */
export interface ISystemJobService {

    /**
     * Lists the system jobs that can be started, ordered by name.
     */
    findSystemJobs(): Promise<SystemJobDescriptor[]>

    /**
     * Starts a run of the named system job, resolving with the run once it and its task ledger are
     * recorded; rejects when no system job has the given name.
     * @param name the name of the job to run
     */
    startSystemJob(name: string): Promise<JobRun>

}

export class SystemJobService implements ISystemJobService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${SYSTEM_API_ZONE}~org.kinotic.system.api.services.SystemJobService`)
    }

    public findSystemJobs(): Promise<SystemJobDescriptor[]> {
        return this.serviceProxy.invoke('findSystemJobs', [])
    }

    public startSystemJob(name: string): Promise<JobRun> {
        return this.serviceProxy.invoke('startSystemJob', [name])
    }

}
