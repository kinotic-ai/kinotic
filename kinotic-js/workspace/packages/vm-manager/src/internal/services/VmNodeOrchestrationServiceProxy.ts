import type { IServiceProxy } from '@kinotic-ai/core'
import type { VmNode } from '@kinotic-ai/os-api'
import type { VmNodeRegistration } from '@/model/VmNodeRegistration'

/**
 * Proxy for communicating with the VmNodeOrchestrationService on the Kinotic server.
 * Used by the vm-manager to register itself and send heartbeats.
 */
export class VmNodeOrchestrationServiceProxy {

    private readonly serviceProxy: IServiceProxy

    constructor(serviceProxy: IServiceProxy) {
        this.serviceProxy = serviceProxy
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
     * Sends a heartbeat to indicate this node is still alive.
     * @param nodeId the id of this node
     * @return a Promise resolving to the updated VmNode
     */
    public heartbeat(nodeId: string): Promise<VmNode> {
        return this.serviceProxy.invoke('heartbeat', [nodeId])
    }

    /**
     * Deregisters this node from the orchestrator.
     * @param nodeId the id of this node
     */
    public deregisterNode(nodeId: string): Promise<void> {
        return this.serviceProxy.invoke('deregisterNode', [nodeId])
    }
}
