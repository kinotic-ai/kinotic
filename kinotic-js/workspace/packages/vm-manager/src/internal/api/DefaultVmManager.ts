import type { IVmProvider } from '@/internal/api/providers/IVmProvider'
import type { IVmManager } from '@/api/IVmManager'
import type { AlloyManager } from '@/internal/api/logging/AlloyManager'
import { Publish, Scope } from '@kinotic-ai/core'
import type { Workload } from '@kinotic-ai/os-api'

/**
 * Default implementation of {@link IVmManager}.
 *
 * The {@link Scope} getter ensures this service registers with a scope equal to
 * the node's unique id, allowing the orchestrator to route requests to a specific node's VmManager.
 */
// Published as 'VmManager', not the class name, so the address matches the orchestrator's
// VmManagerProxy: srv://<nodeId>@system~kinotic-ai.vm-manager.VmManager
@Publish('kinotic-ai.vm-manager', 'VmManager')
export class DefaultVmManager implements IVmManager {

    public readonly nodeId: string

    private readonly provider: IVmProvider
    private readonly alloyManager: AlloyManager | null

    @Scope
    get scope(): string {
        return this.nodeId
    }

    /**
     * @param provider the provider every workload on this node runs on, chosen by the node's
     *        configuration rather than by the workloads it is given
     */
    constructor(nodeId: string, provider: IVmProvider, alloyManager: AlloyManager | null = null) {
        this.nodeId = nodeId
        this.provider = provider
        this.alloyManager = alloyManager
    }

    async startWorkload(workload: Workload): Promise<Workload> {
        const started = await this.provider.start(workload)
        await this.refreshLogShipping()
        return started
    }

    async restartWorkload(workloadId: string): Promise<Workload> {
        const restarted = await this.provider.restart(workloadId)
        await this.refreshLogShipping()
        return restarted
    }

    async stopWorkload(workloadId: string): Promise<void> {
        await this.provider.stop(workloadId)
        await this.refreshLogShipping()
    }

    async destroyWorkload(workloadId: string): Promise<void> {
        await this.provider.destroy(workloadId)
        await this.refreshLogShipping()
    }

    async getWorkload(workloadId: string): Promise<Workload> {
        return this.provider.getWorkload(workloadId)
    }

    async listWorkloads(): Promise<Workload[]> {
        return this.provider.listWorkloads()
    }

    /**
     * Rebuilds the log shipping pipeline from the workloads currently running on this node,
     * launching the log shipper if it is not up yet. Every workload operation ends in a
     * refresh; calling it once at startup, after the provider has recovered, both resumes
     * shipping for the recovered workloads and pays the shipper's first-run cost off the
     * path of the first workload operation.
     */
    // Log shipping must never fail a workload operation, so errors are logged and swallowed
    async refreshLogShipping(): Promise<void> {
        if (!this.alloyManager) {
            return
        }
        try {
            await this.alloyManager.applyTargets(await this.provider.listLogTargets())
        } catch (error) {
            console.error('Failed to update log shipping configuration:', error)
        }
    }
}
