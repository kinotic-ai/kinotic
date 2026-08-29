import { SYSTEM_API_ZONE } from '@kinotic-ai/management-api'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import { Workload } from '@/api/model/workload/Workload'

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
     * Restarts a stopped workload in place on the node it is deployed to. Fails unless the
     * workload is stopped; a workload stopped with autoRemove true has no VM left to restart.
     * Honors the workload's detached flag the same way as {@link deployWorkload}.
     * @param workloadId the id of the workload to restart
     * @return a Promise resolving to the restarted workload
     */
    restartWorkload(workloadId: string): Promise<Workload>

    /**
     * Stops a running workload.
     * @param workloadId the id of the workload to stop
     * @return a Promise that resolves when the workload has been stopped
     */
    stopWorkload(workloadId: string): Promise<void>

    /**
     * Destroys a workload, removing it from the node and cleaning up all resources.
     * @param workloadId the id of the workload to destroy
     * @return a Promise that resolves when the workload has been destroyed
     */
    destroyWorkload(workloadId: string): Promise<void>

}

export class WorkloadOrchestrationService implements IWorkloadOrchestrationService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${SYSTEM_API_ZONE}~org.kinotic.system.api.services.WorkloadOrchestrationService`)
    }

    public deployWorkload(workload: Workload): Promise<Workload> {
        return this.serviceProxy.invoke('deployWorkload', [workload])
    }

    public restartWorkload(workloadId: string): Promise<Workload> {
        return this.serviceProxy.invoke('restartWorkload', [workloadId])
    }

    public stopWorkload(workloadId: string): Promise<void> {
        return this.serviceProxy.invoke('stopWorkload', [workloadId])
    }

    public destroyWorkload(workloadId: string): Promise<void> {
        return this.serviceProxy.invoke('destroyWorkload', [workloadId])
    }

}
