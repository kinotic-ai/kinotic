import type { IVmProvider } from '@/api/providers/IVmProvider'
import { BoxliteProvider } from '@/api/providers/BoxliteProvider'
import { Publish, Scope } from '@kinotic-ai/core'
import { type Workload, VmProviderType } from '@kinotic-ai/os-api'

/**
 * VmManager is the main service that manages VM workloads.
 * It delegates to the appropriate {@link IVmProvider} based on the workload's provider type.
 *
 * The {@link Scope} decorator on nodeId ensures this service registers with a scope equal to
 * the node's unique id, allowing the orchestrator to route requests to a specific node's VmManager.
 */
@Publish('org.kinotic.os.api.services')
export class VmManager {

    @Scope
    public readonly nodeId: string

    private readonly providers: Map<VmProviderType, IVmProvider> = new Map()

    constructor(nodeId: string) {
        this.nodeId = nodeId
        this.providers.set(VmProviderType.BOXLITE, new BoxliteProvider())
    }

    async startWorkload(workload: Workload): Promise<Workload> {
        const provider = this.getProvider(workload.providerType)
        return provider.start(workload)
    }

    async stopWorkload(workloadId: string): Promise<void> {
        const provider = await this.findProviderForWorkload(workloadId)
        return provider.stop(workloadId)
    }

    async destroyWorkload(workloadId: string): Promise<void> {
        const provider = await this.findProviderForWorkload(workloadId)
        return provider.destroy(workloadId)
    }

    async getWorkload(workloadId: string): Promise<Workload> {
        const provider = await this.findProviderForWorkload(workloadId)
        return provider.getWorkload(workloadId)
    }

    async listWorkloads(): Promise<Workload[]> {
        const allWorkloads: Workload[] = []
        for (const provider of this.providers.values()) {
            const workloads = await provider.listWorkloads()
            allWorkloads.push(...workloads)
        }
        return allWorkloads
    }

    private getProvider(providerType: VmProviderType): IVmProvider {
        const provider = this.providers.get(providerType)
        if (!provider) {
            throw new Error(`Unsupported VM provider type: ${providerType}`)
        }
        return provider
    }

    private async findProviderForWorkload(workloadId: string): Promise<IVmProvider> {
        for (const provider of this.providers.values()) {
            try {
                await provider.getWorkload(workloadId)
                return provider
            } catch {
                // not found in this provider, continue
            }
        }
        throw new Error(`Workload not found across any provider: ${workloadId}`)
    }
}
