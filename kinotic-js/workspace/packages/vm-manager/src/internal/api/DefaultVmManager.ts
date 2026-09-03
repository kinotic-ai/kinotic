import type { IVmProvider } from '@/internal/api/providers/IVmProvider'
import type { IVmManager } from '@/api/IVmManager'
import type { AlloyManager } from '@/internal/api/telemetry/AlloyManager'
import { Publish, Scope } from '@kinotic-ai/core'
import { NetworkMode, type Workload } from '@kinotic-ai/management-api'

/**
 * Default implementation of {@link IVmManager}.
 *
 * The {@link Scope} getter ensures this service registers with a scope equal to
 * the node's unique id, allowing the orchestrator to route requests to a specific node's VmManager.
 */
// Published as 'VmManager', not the class name, so the address matches the orchestrator's
// VmManagerProxy: srv://<nodeId>@system-api~kinotic-ai.vm-manager.VmManager
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
        return this.logged('startWorkload', `${workload.name} [${workload.id}] image=${workload.image} vcpus=${workload.vcpus} memoryMb=${workload.memoryMb}`, async () => {
            // A workload with no network has no interface to publish a port on, and no way to
            // reach the node's OTLP endpoint. Rejected here rather than in a provider, so the
            // answer does not depend on where it is placed.
            const networkMode = workload.network?.mode ?? NetworkMode.ENABLED
            if (networkMode === NetworkMode.DISABLED && workload.portMappings.length > 0) {
                throw new Error(`Workload ${workload.name} declares network.mode DISABLED and `
                                + `${workload.portMappings.length} port mapping(s): a workload `
                                + 'with no network has no interface to publish a port on')
            }
            const telemetry = workload.telemetry ?? false
            if (networkMode === NetworkMode.DISABLED && telemetry) {
                throw new Error(`Workload ${workload.name} declares network.mode DISABLED and elects `
                                + "telemetry: a workload with no network cannot reach the node's OTLP endpoint")
            }
            // Runs without an endpoint rather than being refused: a node that ships nothing said
            // so at startup, and the election is the workload's to make wherever it lands
            if (telemetry && !(this.alloyManager?.shipsTelemetry() ?? false)) {
                console.warn(`Workload ${workload.name} elects telemetry, but this node ships neither `
                             + 'traces nor metrics (KINOTIC_TEMPO_URL and KINOTIC_MIMIR_URL are not set)')
            }
            const started = await this.provider.start(workload)
            await this.refreshShipping()
            return this.settled(started)
        })
    }

    async restartWorkload(workloadId: string): Promise<Workload> {
        return this.logged('restartWorkload', workloadId, async () => {
            const restarted = await this.provider.restart(workloadId)
            await this.refreshShipping()
            return this.settled(restarted)
        })
    }

    // A non-detached workload runs in the foreground: its reply carries the run's outcome.
    // Log shipping is configured before the wait, so a run over in seconds is still tailed
    private settled(workload: Workload): Promise<Workload> {
        return (workload.detached ?? true) ? Promise.resolve(workload) : this.provider.awaitExit(workload.id!)
    }

    async stopWorkload(workloadId: string): Promise<void> {
        return this.logged('stopWorkload', workloadId, async () => {
            await this.provider.stop(workloadId)
            await this.refreshShipping()
        })
    }

    async destroyWorkload(workloadId: string): Promise<void> {
        return this.logged('destroyWorkload', workloadId, async () => {
            // Destroy deletes the log files, so what the shipper has not read yet goes first
            const target = (await this.provider.listTelemetryTargets()).find(t => t.workloadId === workloadId)
            if (target && this.alloyManager) {
                await this.alloyManager.awaitShipped(target)
            }
            await this.provider.destroy(workloadId)
            await this.refreshShipping()
        })
    }

    async getWorkload(workloadId: string): Promise<Workload> {
        return this.logged('getWorkload', workloadId, async () => {
            return this.provider.getWorkload(workloadId)
        })
    }

    async listWorkloads(): Promise<Workload[]> {
        return this.logged('listWorkloads', '', async () => {
            return this.provider.listWorkloads()
        })
    }

    /**
     * Logs one published call and its outcome. The node is the far end of the orchestrator's
     * dispatch, so a deploy that stalls needs to distinguish "the request never arrived" from
     * "the request arrived and the provider is still working on it".
     */
    private async logged<T>(method: string, detail: string, operation: () => Promise<T>): Promise<T> {
        const startedAt = Date.now()
        console.log(`[vm-manager] <- ${method}(${detail})`)
        try {
            const result = await operation()
            console.log(`[vm-manager] -> ${method} ok in ${Date.now() - startedAt}ms`)
            return result
        } catch (error) {
            console.error(`[vm-manager] -> ${method} failed in ${Date.now() - startedAt}ms:`, error)
            throw error
        }
    }

    /**
     * Rebuilds the telemetry shipping pipeline from the workloads currently running on this
     * node, launching the shipper if it is not up yet. Every workload operation ends in a
     * refresh; calling it once at startup, after the provider has recovered, both resumes
     * shipping for the recovered workloads and pays the shipper's first-run cost off the
     * path of the first workload operation.
     */
    // Shipping must never fail a workload operation, so errors are logged and swallowed
    async refreshShipping(): Promise<void> {
        if (!this.alloyManager) {
            return
        }
        try {
            await this.alloyManager.applyTargets(await this.provider.listTelemetryTargets())
        } catch (error) {
            console.error('Failed to update telemetry shipping configuration:', error)
        }
    }
}
