import { SYSTEM_API_ZONE, Workload } from '@kinotic-ai/management-api'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'

/**
 * Orchestrates workload deployment across the cluster, acting as the intermediary between
 * clients and the VmManager on each node. For querying workloads use {@link IWorkloadService}.
 */
export interface IWorkloadOrchestrationService {

    /**
     * Deploys a new workload to an appropriate node in the cluster. A workload carrying a
     * nodeId is deployed to that node instead, failing when the node is not registered, not
     * taking workloads, or lacks the capacity the workload requires. When the workload is not
     * detached the returned Promise resolves only once the run has ended — STOPPED or
     * FAILED, with the exit code set; otherwise it resolves as soon as the workload is
     * started.
     * @param workload the workload configuration to deploy
     * @return a Promise resolving to the deployed workload (including assigned nodeId and id)
     */
    deployWorkload(workload: Workload): Promise<Workload>

    /**
     * Stops a running workload.
     * @param workloadId the id of the workload to stop
     * @return a Promise that resolves when the workload has been stopped
     */
    stopWorkload(workloadId: string): Promise<void>

    /**
     * Destroys a workload's VM on its node, with its disk, and returns its room. A run still open is
     * recorded STOPPED. The record stays as the run's outcome, and its logs stay in the log store,
     * until deleteWorkload.
     * @param workloadId the id of the workload to destroy
     * @return a Promise that resolves when the VM has been destroyed
     */
    destroyWorkload(workloadId: string): Promise<void>

    /**
     * Deletes a workload's record and every log line it wrote from the organization's log store, the
     * one way either is removed. A run still open on its node — starting, running, or a stop the node
     * has not answered — is refused: stop or destroy it first.
     * @param workloadId the id of the workload to delete
     * @return a Promise that resolves when the record and its logs are gone, or rejects if the run is
     *         still open
     */
    deleteWorkload(workloadId: string): Promise<void>

}

export class WorkloadOrchestrationService implements IWorkloadOrchestrationService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${SYSTEM_API_ZONE}~org.kinotic.system.api.services.WorkloadOrchestrationService`)
    }

    public deployWorkload(workload: Workload): Promise<Workload> {
        return this.serviceProxy.invoke('deployWorkload', [workload])
    }

    public stopWorkload(workloadId: string): Promise<void> {
        return this.serviceProxy.invoke('stopWorkload', [workloadId])
    }

    public destroyWorkload(workloadId: string): Promise<void> {
        return this.serviceProxy.invoke('destroyWorkload', [workloadId])
    }

    public deleteWorkload(workloadId: string): Promise<void> {
        return this.serviceProxy.invoke('deleteWorkload', [workloadId])
    }

}
