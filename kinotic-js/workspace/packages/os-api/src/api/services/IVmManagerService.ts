import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { Workload } from '@/api/model/Workload'

/**
 * Service interface for managing VM workloads.
 * This service communicates with the vm-manager process to start, stop, and manage micro VM workloads.
 */
export interface IVmManagerService {

    /**
     * Starts a new workload with the given configuration.
     * @param workload the workload configuration to start
     * @return a Promise resolving to the started workload with updated status and id
     */
    startWorkload(workload: Workload): Promise<Workload>

    /**
     * Stops a running workload.
     * @param workloadId the id of the workload to stop
     * @return a Promise that resolves when the workload has been stopped
     */
    stopWorkload(workloadId: string): Promise<void>

    /**
     * Destroys a workload, removing it entirely.
     * @param workloadId the id of the workload to destroy
     * @return a Promise that resolves when the workload has been destroyed
     */
    destroyWorkload(workloadId: string): Promise<void>

    /**
     * Gets the current state of a workload.
     * @param workloadId the id of the workload to get
     * @return a Promise resolving to the workload
     */
    getWorkload(workloadId: string): Promise<Workload>

    /**
     * Lists all workloads managed by this vm-manager instance.
     * @return a Promise resolving to an array of all workloads
     */
    listWorkloads(): Promise<Workload[]>
}

export class VmManagerService implements IVmManagerService {
    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy('org.kinotic.os.api.services.VmManagerService')
    }

    public startWorkload(workload: Workload): Promise<Workload> {
        return this.serviceProxy.invoke('startWorkload', [workload])
    }

    public stopWorkload(workloadId: string): Promise<void> {
        return this.serviceProxy.invoke('stopWorkload', [workloadId])
    }

    public destroyWorkload(workloadId: string): Promise<void> {
        return this.serviceProxy.invoke('destroyWorkload', [workloadId])
    }

    public getWorkload(workloadId: string): Promise<Workload> {
        return this.serviceProxy.invoke('getWorkload', [workloadId])
    }

    public listWorkloads(): Promise<Workload[]> {
        return this.serviceProxy.invoke('listWorkloads', [])
    }
}
