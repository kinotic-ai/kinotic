import { SimpleBox, getJsBoxlite, type SimpleBoxOptions } from '@boxlite-ai/boxlite'
import { mkdirSync, readdirSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import type { IVmProvider } from '@/internal/api/providers/IVmProvider'
import type { LogTarget } from '@/model/LogTarget'
import { Workload, WorkloadStatus, VmProviderType } from '@kinotic-ai/os-api'

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
    // Silently binding to all interfaces when a specific one was requested would be a
    // security failure, so an unsupported hostIp is rejected outright
    const boundMapping = workload.portMappings.find(mapping => mapping.hostIp)
    if (boundMapping) {
        throw new Error(`boxlite cannot bind a specific host interface (hostIp ${boundMapping.hostIp})`)
    }
    return {
        image: workload.image,
        name: workload.id!,
        cpus: workload.vcpus,
        memoryMib: workload.memoryMb,
        env: workload.environment,
        ...(workload.entrypoint.length > 0 ? { entrypoint: workload.entrypoint } : {}),
        ...(workload.cmd.length > 0 ? { cmd: workload.cmd } : {}),
        ports: workload.portMappings.map(({ hostPort, guestPort, protocol }) => ({
            ...(hostPort !== undefined ? { hostPort } : {}),
            guestPort,
            // boxlite recognizes only lowercase 'udp'; any other value silently means tcp
            ...(protocol !== undefined ? { protocol: protocol.toLowerCase() } : {}),
        })),
        volumes: [
            ...workload.volumeMounts.map(({ hostPath, guestPath, readOnly }) => ({
                hostPath,
                guestPath,
                readOnly,
            })),
            { hostPath: logDir, guestPath: GUEST_LOG_DIR },
        ],
        autoRemove: workload.autoRemove,
        detach: workload.detached,
    }
}

/**
 * VM provider implementation using the boxlite Node.js SDK for micro VM management.
 * Workload state is persisted under the given state directory so a restarted vm-manager
 * can {@link recover} the VMs of a previous process.
 * @see https://github.com/boxlite-ai/boxlite
 */
export class BoxliteProvider implements IVmProvider {

    readonly type: VmProviderType = VmProviderType.BOXLITE

    private readonly workloads: Map<string, Workload> = new Map()
    private readonly activeVms: Map<string, ActiveVm> = new Map()
    private readonly logsBaseDir: string
    // State must not live under logsBaseDir: log dirs are guest-writable via the
    // GUEST_LOG_DIR mount, and a guest could rewrite its organizationId to reroute
    // its logs to another tenant
    private readonly stateDir: string
    // One boxlite runtime for the whole provider; every SimpleBox is bound to it
    private readonly runtime = getJsBoxlite().withDefaultConfig()

    constructor(logsBaseDir: string, stateDir: string) {
        this.logsBaseDir = logsBaseDir
        this.stateDir = stateDir
        mkdirSync(stateDir, { recursive: true })
    }

    async recover(): Promise<void> {
        for (const file of readdirSync(this.stateDir)) {
            if (!file.endsWith('.json')) {
                continue
            }
            let workload: Workload
            try {
                workload = JSON.parse(readFileSync(join(this.stateDir, file), 'utf-8'))
            } catch (error) {
                console.error(`Skipping unreadable workload state file ${file}:`, error)
                continue
            }
            this.workloads.set(workload.id!, workload)
            await this.recoverVm(workload)
        }
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
        // STARTING is persisted first so a crash mid-boot is visible to recover()
        this.persist(workload)

        try {
            // A box record left by a previous run of this workload would collide on the name
            const stale = await this.runtime.getInfo(id)
            if (stale && !stale.state.running) {
                await this.runtime.remove(id, true)
            }

            const box = new SimpleBox({ ...buildBoxOptions(workload, logDir), runtime: this.runtime })

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
            this.persist(workload)
        }

        return workload
    }

    async restart(workloadId: string): Promise<Workload> {
        const workload = this.workloads.get(workloadId)
        if (!workload) {
            throw new Error(`Workload not found: ${workloadId}`)
        }
        if (workload.status !== WorkloadStatus.STOPPED) {
            throw new Error(`Workload ${workloadId} is not stopped (status: ${workload.status})`)
        }
        const info = await this.runtime.getInfo(workloadId)
        if (!info) {
            throw new Error(`Workload ${workloadId} cannot be restarted — its VM was discarded (autoRemove)`)
        }

        workload.status = WorkloadStatus.STARTING
        workload.updated = Date.now()
        this.persist(workload)

        try {
            const jsbox = await this.runtime.get(workloadId)
            if (!jsbox) {
                throw new Error(`Box not found for workload: ${workloadId}`)
            }
            await jsbox.start()

            const logDir = join(this.logsBaseDir, workloadId)
            const box = new SimpleBox({ ...buildBoxOptions(workload, logDir), runtime: this.runtime, reuseExisting: true })
            // Attach now: stop() on a SimpleBox whose box was never resolved is a no-op
            await box.getId()
            this.activeVms.set(workloadId, { box, vmId: info.id, logDir })

            workload.status = WorkloadStatus.RUNNING
        } catch (error) {
            workload.status = WorkloadStatus.FAILED
            this.activeVms.delete(workloadId)
            throw error
        } finally {
            workload.updated = Date.now()
            this.persist(workload)
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
        this.persist(workload)

        await vm.box.stop()

        workload.status = WorkloadStatus.STOPPED
        workload.updated = Date.now()
        // The log dir is kept so already-written logs remain shippable until destroy
        this.activeVms.delete(workloadId)
        this.persist(workload)
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

        // autoRemove is off, so the box record survives stop and must be removed explicitly
        if (await this.runtime.getInfo(workloadId)) {
            await this.runtime.remove(workloadId, true)
        }

        rmSync(join(this.logsBaseDir, workloadId), { recursive: true, force: true })
        rmSync(this.stateFile(workloadId), { force: true })
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

    // Reconciles a persisted workload with the actual box state, reattaching when it is
    // still running
    private async recoverVm(workload: Workload): Promise<void> {
        if (workload.status !== WorkloadStatus.STARTING &&
            workload.status !== WorkloadStatus.RUNNING &&
            workload.status !== WorkloadStatus.STOPPING) {
            return
        }
        const id = workload.id!
        try {
            const info = await this.runtime.getInfo(id)
            if (info?.state.running) {
                const logDir = join(this.logsBaseDir, id)
                const box = new SimpleBox({
                    ...buildBoxOptions(workload, logDir),
                    runtime: this.runtime,
                    reuseExisting: true,
                })
                // Attach now: stop() on a SimpleBox whose box was never resolved is a no-op
                await box.getId()
                this.activeVms.set(id, { box, vmId: info.id, logDir })
                workload.status = WorkloadStatus.RUNNING
                console.log(`Reattached to running workload ${id} (vm ${info.id})`)
            } else {
                // The box ended while no vm-manager was supervising it
                workload.status = workload.status === WorkloadStatus.STOPPING
                    ? WorkloadStatus.STOPPED
                    : WorkloadStatus.FAILED
            }
        } catch (error) {
            console.error(`Failed to reattach workload ${id}:`, error)
            workload.status = WorkloadStatus.FAILED
        }
        workload.updated = Date.now()
        this.persist(workload)
    }

    // Written atomically (write + rename) so a crash mid-write cannot corrupt recovery state
    private persist(workload: Workload): void {
        const file = this.stateFile(workload.id!)
        writeFileSync(`${file}.tmp`, JSON.stringify(workload))
        renameSync(`${file}.tmp`, file)
    }

    private stateFile(workloadId: string): string {
        return join(this.stateDir, `${workloadId}.json`)
    }
}
