import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import { SYSTEM_API_ZONE } from '@/api/SystemZone'
import type { VmNode } from '@/api/model/workload/VmNode'
import type { VmNodeRegistration } from '@/api/model/VmNodeRegistration'
import type { WorkloadStatusReport } from '@/api/model/WorkloadStatusReport'

/**
 * Proxy for communicating with the VmNodeOrchestrationService on the Kinotic server.
 * Used by the vm-manager to register itself and send heartbeats.
 */
export class VmNodeOrchestrationServiceProxy {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(
            `${SYSTEM_API_ZONE}~org.kinotic.system.api.services.VmNodeOrchestrationService`)
    }

    /**
     * Registers this node with the orchestrator.
     * @param registration the node registration info
     * @return a Promise resolving to the registered VmNode
     */
    public registerNode(registration: VmNodeRegistration): Promise<VmNode> {
        return this.serviceProxy.invoke('registerNode', [registration])
    }

    /**
     * Sends a heartbeat to indicate this node is still alive, carrying what it can still
     * guarantee. A node reporting problems stops receiving workloads until it reports none.
     * @param nodeId the id of this node
     * @param problems what the node can no longer guarantee, empty when it is fit
     * @return a Promise resolving to the updated VmNode
     */
    public heartbeat(nodeId: string, problems: string[]): Promise<VmNode> {
        return this.serviceProxy.invoke('heartbeat', [nodeId, problems])
    }

    /**
     * Reports the actual status of workloads on this node so the server's records track
     * transitions the orchestrator did not initiate.
     * @param nodeId the id of this node
     * @param reports one report per workload
     */
    public reportWorkloadStatus(nodeId: string, reports: WorkloadStatusReport[]): Promise<void> {
        return this.serviceProxy.invoke('reportWorkloadStatus', [nodeId, reports])
    }

    /**
     * Deregisters this node from the orchestrator.
     * @param nodeId the id of this node
     */
    public deregisterNode(nodeId: string): Promise<void> {
        return this.serviceProxy.invoke('deregisterNode', [nodeId])
    }
}
