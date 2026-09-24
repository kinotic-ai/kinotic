import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy, Page, Pageable } from '@kinotic-ai/core'
import type { Workload } from '@/api/model/workload/Workload'

/**
 * The workloads the platform runs on behalf of the caller's organization: the long-lived VM of
 * each project microservice, and the one-off runs that sync its projects and publish or remove
 * their UIs. A workload whose run has ended keeps its record, with the status and exit code the
 * run ended on.
 * A workload's logs are read through ILogService, its traces and metrics through
 * ITelemetryService.
 */
export interface IWorkloadMonitoringService {

    /**
     * Finds the workloads of the caller's organization.
     * @param pageable the page of workloads to return
     */
    findWorkloads(pageable: Pageable): Promise<Page<Workload>>

    /**
     * Finds the workloads of one of the caller's organization's applications.
     * @param applicationId an application of the caller's organization
     * @param pageable the page of workloads to return
     */
    findWorkloadsForApplication(applicationId: string, pageable: Pageable): Promise<Page<Workload>>

    /**
     * Finds a single workload of the caller's organization; rejects when the organization has no
     * workload with that id.
     * @param workloadId the id of the workload
     */
    findWorkload(workloadId: string): Promise<Workload>
}

export class WorkloadMonitoringService implements IWorkloadMonitoringService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.WorkloadMonitoringService`)
    }

    public findWorkloads(pageable: Pageable): Promise<Page<Workload>> {
        return this.serviceProxy.invoke('findWorkloads', [pageable])
    }

    public findWorkloadsForApplication(applicationId: string, pageable: Pageable): Promise<Page<Workload>> {
        return this.serviceProxy.invoke('findWorkloadsForApplication', [applicationId, pageable])
    }

    public findWorkload(workloadId: string): Promise<Workload> {
        return this.serviceProxy.invoke('findWorkload', [workloadId])
    }
}
