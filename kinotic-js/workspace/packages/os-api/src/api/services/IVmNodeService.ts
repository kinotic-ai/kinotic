import type { IKinotic } from '@kinotic-ai/core'
import { CrudServiceProxy, type ICrudServiceProxy } from '@kinotic-ai/core'
import { VmNode } from '@/api/model/VmNode'


export interface IVmNodeService extends ICrudServiceProxy<VmNode> {

    /**
     * Finds a node with sufficient resources to host a workload with the given requirements.
     * @param requiredCpus the number of vCPUs required
     * @param requiredMemoryMb the amount of memory required in megabytes
     * @param requiredDiskMb the amount of disk space required in megabytes
     * @return a Promise resolving to a suitable node, or null if none available
     */
    findAvailableNode(requiredCpus: number, requiredMemoryMb: number, requiredDiskMb: number): Promise<VmNode | null>

    /**
     * This operation makes all the recent writes immediately available for search.
     * @return a Promise that resolves when the operation is complete
     */
    syncIndex(): Promise<void>

}

export class VmNodeServiceProxy extends CrudServiceProxy<VmNode> implements IVmNodeService {

    constructor(kinotic: IKinotic) {
        super(kinotic.serviceProxy('org.kinotic.os.api.services.VmNodeService'))
    }

    public findAvailableNode(requiredCpus: number, requiredMemoryMb: number, requiredDiskMb: number): Promise<VmNode | null> {
        return this.serviceProxy.invoke('findAvailableNode', [requiredCpus, requiredMemoryMb, requiredDiskMb])
    }

    public syncIndex(): Promise<void> {
        return this.serviceProxy.invoke('syncIndex', [])
    }

}
