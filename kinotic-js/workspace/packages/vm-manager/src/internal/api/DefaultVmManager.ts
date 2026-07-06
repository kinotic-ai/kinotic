import type { IVmProvider } from '@/internal/api/providers/IVmProvider'
import { BoxliteProvider } from '@/internal/api/providers/BoxliteProvider'
import type { IVmManager } from '@/api/IVmManager'
import type { AlloyManager } from '@/internal/api/logging/AlloyManager'
import type { LogTarget } from '@/model/LogTarget'
import { Publish, Scope } from '@kinotic-ai/core'
import { type Workload, VmProviderType } from '@kinotic-ai/os-api'

/**
 * Default implementation of {@link IVmManager}.
 *
 * The {@link Scope} decorator on nodeId ensures this service registers with a scope equal to
 * the node's unique id, allowing the orchestrator to route requests to a specific node's VmManager.
 */
@Publish('kinotic-ai.vm-manager')
export class DefaultVmManager implements IVmManager {

    @Scope
    public readonly nodeId: string

    private readonly providers: Map<VmProviderType, IVmProvider> = new Map()
    private readonly alloyManager: AlloyManager | null

    constructor(nodeId: string, vmLogsDir: string, alloyManager: AlloyManager | null = null) {
        this.nodeId = nodeId
        this.alloyManager = alloyManager
        this.providers.set(VmProviderType.BOXLITE, new BoxliteProvider(vmLogsDir))
    }

    async startWorkload(workload: Workload): Promise<Workload> {
        const provider = this.getProvider(workload.providerType)
        const started = await provider.start(workload)
        await this.refreshLogShipping()
        return started
    }

    async stopWorkload(workloadId: string): Promise<void> {
        const provider = await this.findProviderForWorkload(workloadId)
        await provider.stop(workloadId)
        await this.refreshLogShipping()
    }

    async destroyWorkload(workloadId: string): Promise<void> {
        const provider = await this.findProviderForWorkload(workloadId)
        await provider.destroy(workloadId)
        await this.refreshLogShipping()
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

    // Log shipping must never fail a workload operation, so errors are logged and swallowed
    private async refreshLogShipping(): Promise<void> {
        if (!this.alloyManager) {
            return
        }
        try {
            const targets: LogTarget[] = []
            for (const provider of this.providers.values()) {
                targets.push(...await provider.listLogTargets())
            }
            await this.alloyManager.applyTargets(targets)
        } catch (error) {
            console.error('Failed to update log shipping configuration:', error)
        }
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
