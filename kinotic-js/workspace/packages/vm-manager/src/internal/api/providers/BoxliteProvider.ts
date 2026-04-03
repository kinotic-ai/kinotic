import { SimpleBox } from '@boxlite-ai/boxlite'
import type { IVmProvider } from '@/internal/api/providers/IVmProvider'
import { Workload, WorkloadStatus } from '@kinotic-ai/os-api'

/**
 * VM provider implementation using the boxlite Node.js SDK for micro VM management.
 * @see https://github.com/boxlite-ai/boxlite
 */
export class BoxliteProvider implements IVmProvider {

    private readonly workloads: Map<string, Workload> = new Map()
    private readonly boxes: Map<string, SimpleBox> = new Map()

    async start(workload: Workload): Promise<Workload> {
        const id = workload.id ?? crypto.randomUUID()
        workload.id = id
        workload.status = WorkloadStatus.STARTING
        workload.created = Date.now()
        workload.updated = Date.now()

        this.workloads.set(id, workload)

        try {
            const box = new SimpleBox({
                image: workload.image,
                name: id,
                cpus: workload.vcpus,
                memoryMib: workload.memoryMb,
                env: workload.environment,
                ports: Object.entries(workload.portMappings).map(([hostPort, guestPort]) => ({
                    hostPort: Number(hostPort),
                    guestPort: Number(guestPort),
                })),
                autoRemove: false,
            })

            this.boxes.set(id, box)

            // Verify the box is responsive
            await box.exec('echo ready')

            workload.status = WorkloadStatus.RUNNING
        } catch (error) {
            workload.status = WorkloadStatus.FAILED
            this.boxes.delete(id)
            throw error
        } finally {
            workload.updated = Date.now()
        }

        return workload
    }

    async stop(workloadId: string): Promise<void> {
        const workload = this.workloads.get(workloadId)
        if (!workload) {
            throw new Error(`Workload not found: ${workloadId}`)
        }

        const box = this.boxes.get(workloadId)
        if (!box) {
            throw new Error(`Box not found for workload: ${workloadId}`)
        }

        workload.status = WorkloadStatus.STOPPING
        workload.updated = Date.now()

        await box.stop()

        workload.status = WorkloadStatus.STOPPED
        workload.updated = Date.now()
        this.boxes.delete(workloadId)
    }

    async destroy(workloadId: string): Promise<void> {
        const workload = this.workloads.get(workloadId)
        if (!workload) {
            throw new Error(`Workload not found: ${workloadId}`)
        }

        const box = this.boxes.get(workloadId)
        if (box) {
            await box.stop()
            this.boxes.delete(workloadId)
        }

        this.workloads.delete(workloadId)
    }

    async getWorkload(workloadId: string): Promise<Workload> {
        const workload = this.workloads.get(workloadId)
        if (!workload) {
            throw new Error(`Workload not found: ${workloadId}`)
        }
        return workload
    }

    async listWorkloads(): Promise<Workload[]> {
        return Array.from(this.workloads.values())
    }
}
