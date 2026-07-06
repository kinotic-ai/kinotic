import { SimpleBox, type SimpleBoxOptions } from '@boxlite-ai/boxlite'
import { mkdirSync, rmSync } from 'node:fs'
import { join } from 'node:path'
import type { IVmProvider } from '@/internal/api/providers/IVmProvider'
import type { LogTarget } from '@/model/LogTarget'
import { Workload, WorkloadStatus } from '@kinotic-ai/os-api'

/**
 * Guest path where the per-workload host log directory is mounted. This is the log-shipping
 * contract: log files a workload writes under this directory are shipped to Loki. boxlite
 * provides no host-side capture of the entrypoint's stdout/stderr, so workload images must
 * write their logs here themselves.
 */
export const GUEST_LOG_DIR = '/var/log/kinotic'

/**
 * A VM currently managed by this provider, with the resources that outlive the box itself.
 */
export interface ActiveVm {
    box: SimpleBox
    /** boxlite box id (ULID), assigned once the VM boots. */
    vmId: string
    /** Host directory holding this VM's log files, mounted at {@link GUEST_LOG_DIR}. */
    logDir: string
}

/**
 * Builds the boxlite options for a workload. The given host log directory is always mounted
 * at {@link GUEST_LOG_DIR}; entrypoint and cmd are passed through only when the workload
 * declares them, so an empty value keeps the image default.
 */
export function buildBoxOptions(workload: Workload, logDir: string): SimpleBoxOptions {
    return {
        image: workload.image,
        name: workload.id!,
        cpus: workload.vcpus,
        memoryMib: workload.memoryMb,
        env: workload.environment,
        ...(workload.entrypoint.length > 0 ? { entrypoint: workload.entrypoint } : {}),
        ...(workload.cmd.length > 0 ? { cmd: workload.cmd } : {}),
        ports: Object.entries(workload.portMappings).map(([hostPort, guestPort]) => ({
            hostPort: Number(hostPort),
            guestPort: Number(guestPort),
        })),
        volumes: [
            ...workload.volumeMounts.map(({ hostPath, guestPath, readOnly }) => ({
                hostPath,
                guestPath,
                readOnly,
            })),
            { hostPath: logDir, guestPath: GUEST_LOG_DIR },
        ],
        autoRemove: false,
    }
}

/**
 * VM provider implementation using the boxlite Node.js SDK for micro VM management.
 * @see https://github.com/boxlite-ai/boxlite
 */
export class BoxliteProvider implements IVmProvider {

    private readonly workloads: Map<string, Workload> = new Map()
    private readonly activeVms: Map<string, ActiveVm> = new Map()
    private readonly logsBaseDir: string

    constructor(logsBaseDir: string) {
        this.logsBaseDir = logsBaseDir
    }

    async start(workload: Workload): Promise<Workload> {
        const id = workload.id ?? crypto.randomUUID()
        workload.id = id
        workload.status = WorkloadStatus.STARTING
        workload.created = Date.now()
        workload.updated = Date.now()

        this.workloads.set(id, workload)

        const logDir = join(this.logsBaseDir, id)
        mkdirSync(logDir, { recursive: true })

        try {
            const box = new SimpleBox(buildBoxOptions(workload, logDir))

            // Verify the box is responsive; also boots the lazily-created VM so box.id is assigned
            await box.exec('echo ready')

            this.activeVms.set(id, { box, vmId: box.id, logDir })

            workload.status = WorkloadStatus.RUNNING
        } catch (error) {
            workload.status = WorkloadStatus.FAILED
            this.activeVms.delete(id)
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

        const vm = this.activeVms.get(workloadId)
        if (!vm) {
            throw new Error(`Box not found for workload: ${workloadId}`)
        }

        workload.status = WorkloadStatus.STOPPING
        workload.updated = Date.now()

        await vm.box.stop()

        workload.status = WorkloadStatus.STOPPED
        workload.updated = Date.now()
        // The log dir is kept so already-written logs remain shippable until destroy
        this.activeVms.delete(workloadId)
    }

    async destroy(workloadId: string): Promise<void> {
        const workload = this.workloads.get(workloadId)
        if (!workload) {
            throw new Error(`Workload not found: ${workloadId}`)
        }

        const vm = this.activeVms.get(workloadId)
        if (vm) {
            await vm.box.stop()
            this.activeVms.delete(workloadId)
        }

        rmSync(join(this.logsBaseDir, workloadId), { recursive: true, force: true })
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

    async listLogTargets(): Promise<LogTarget[]> {
        return Array.from(this.activeVms.entries()).map(([workloadId, vm]) => {
            const workload = this.workloads.get(workloadId)!
            return {
                workloadId,
                vmId: vm.vmId,
                logDir: vm.logDir,
                organizationId: workload.organizationId,
                applicationId: workload.applicationId,
            }
        })
    }
}
