import { SimpleBox } from '@boxlite-ai/boxlite'
import { mkdirSync, rmSync } from 'node:fs'
import { join } from 'node:path'
import type { IVmProvider } from '@/internal/api/providers/IVmProvider'
import { Workload, WorkloadStatus } from '@kinotic-ai/os-api'

/**
 * Guest path where the per-VM host log directory is mounted. Workloads whose entrypoint
 * cannot be wrapped (no explicit entrypoint) write their own log files here to have them shipped.
 */
export const GUEST_LOG_DIR = '/var/log/kinotic'

/**
 * File within {@link GUEST_LOG_DIR} that receives the wrapped workload process's stdout/stderr.
 */
export const CONSOLE_LOG_FILE = 'console.log'

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
 * Builds an entrypoint that appends the workload process's stdout/stderr to the console log
 * on the mounted log volume. Returns null when the workload defines no entrypoint — the image
 * default ENTRYPOINT is resolved inside boxlite and unknown here, so it cannot be wrapped;
 * such workloads ship only the log files they write to {@link GUEST_LOG_DIR} themselves.
 */
export function wrapEntrypointForLogCapture(workload: Workload): string[] | null {
    if (workload.entrypoint.length === 0) {
        return null
    }
    return [
        '/bin/sh', '-c',
        `exec "$@" >> ${GUEST_LOG_DIR}/${CONSOLE_LOG_FILE} 2>&1`,
        'sh',
        ...workload.entrypoint,
        ...workload.cmd,
    ]
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
            const wrappedEntrypoint = wrapEntrypointForLogCapture(workload)
            const box = new SimpleBox({
                image: workload.image,
                name: id,
                cpus: workload.vcpus,
                memoryMib: workload.memoryMb,
                env: workload.environment,
                // cmd was folded into the wrapper's "$@" args, so it is overridden to empty
                ...(wrappedEntrypoint
                    ? { entrypoint: wrappedEntrypoint, cmd: [] }
                    : workload.cmd.length > 0 ? { cmd: workload.cmd } : {}),
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
            })

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
}
