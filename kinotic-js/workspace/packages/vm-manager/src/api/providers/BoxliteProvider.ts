import type { IVmProvider } from '@/api/providers/IVmProvider'
import { Workload, WorkloadStatus } from '@kinotic-ai/os-api'

/**
 * VM provider implementation using boxlite for micro VM management.
 * @see https://github.com/boxlite-ai/boxlite
 */
export class BoxliteProvider implements IVmProvider {

    private readonly workloads: Map<string, Workload> = new Map()

    async start(workload: Workload): Promise<Workload> {
        const id = workload.id ?? crypto.randomUUID()
        workload.id = id
        workload.status = WorkloadStatus.STARTING
        workload.created = Date.now()
        workload.updated = Date.now()

        this.workloads.set(id, workload)

        try {
            const args = this.buildBoxliteArgs(workload)
            const proc = Bun.spawn(['boxlite', 'run', ...args], {
                stdout: 'inherit',
                stderr: 'inherit',
            })

            await proc.exited

            if (proc.exitCode === 0) {
                workload.status = WorkloadStatus.RUNNING
            } else {
                workload.status = WorkloadStatus.FAILED
            }
        } catch (error) {
            workload.status = WorkloadStatus.FAILED
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

        workload.status = WorkloadStatus.STOPPING
        workload.updated = Date.now()

        const proc = Bun.spawn(['boxlite', 'stop', workloadId], {
            stdout: 'inherit',
            stderr: 'inherit',
        })

        await proc.exited

        workload.status = WorkloadStatus.STOPPED
        workload.updated = Date.now()
    }

    async destroy(workloadId: string): Promise<void> {
        const workload = this.workloads.get(workloadId)
        if (!workload) {
            throw new Error(`Workload not found: ${workloadId}`)
        }

        const proc = Bun.spawn(['boxlite', 'rm', '-f', workloadId], {
            stdout: 'inherit',
            stderr: 'inherit',
        })

        await proc.exited

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

    private buildBoxliteArgs(workload: Workload): string[] {
        const args: string[] = []

        args.push('--name', workload.id!)
        args.push('--cpus', String(workload.vcpus))
        args.push('--memory', `${workload.memoryMb}M`)

        for (const [hostPort, guestPort] of Object.entries(workload.portMappings)) {
            args.push('-p', `${hostPort}:${guestPort}`)
        }

        for (const [key, value] of Object.entries(workload.environment)) {
            args.push('-e', `${key}=${value}`)
        }

        args.push(workload.image)

        return args
    }
}
