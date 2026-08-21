import { SYSTEM_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import { Workload } from '@/api/model/workload/Workload'

/**
 * Orchestrates workload deployment across the cluster, acting as the intermediary between
 * clients and the VmManager on each node. For querying workloads use {@link IWorkloadService}.
 */
export interface IWorkloadOrchestrationService {

    /**
     * Deploys a new workload to an appropriate node in the cluster.
     * @param workload the workload configuration to deploy
     * @return a Promise resolving to the deployed workload (including assigned nodeId and id)
     */
    deployWorkload(workload: Workload): Promise<Workload>

    /**
     * Deploys a new workload like {@link deployWorkload} and then waits for its run to end.
     * The returned Promise resolves once the workload reaches STOPPED or FAILED, with the
     * final workload including its exit code, and rejects if the workload is destroyed
     * before its run ends.
     * @param workload the workload configuration to deploy
     * @return a Promise resolving to the finished workload once its run has ended
     */
    runWorkload(workload: Workload): Promise<Workload>

    /**
     * Restarts a stopped workload in place on the node it is deployed to. Fails unless the
     * workload is stopped; a workload stopped with autoRemove true has no VM left to restart.
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
        this.serviceProxy = kinotic.serviceProxy(`${SYSTEM_ZONE}~org.kinotic.orchestrator.api.services.WorkloadOrchestrationService`)
    }

    public deployWorkload(workload: Workload): Promise<Workload> {
        return this.serviceProxy.invoke('deployWorkload', [workload])
    }

    public runWorkload(workload: Workload): Promise<Workload> {
        return this.serviceProxy.invoke('runWorkload', [workload])
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
