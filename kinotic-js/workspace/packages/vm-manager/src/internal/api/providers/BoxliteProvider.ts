import { SimpleBox, getJsBoxlite, type SimpleBoxOptions } from '@boxlite-ai/boxlite'
import { mkdirSync, readdirSync, readFileSync, renameSync, rmSync, statfsSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import type { IVmProvider } from '@/internal/api/providers/IVmProvider'
import type { LogTarget } from '@/model/LogTarget'
import { LogFormat } from '@/model/LogFormat'
import { Workload, WorkloadStatus, VmProviderType, NetworkMode } from '@kinotic-ai/os-api'

/**
 * Guest path where the per-workload host log directory is mounted. This is the log-shipping
 * contract: log files a workload writes under this directory are shipped to Loki. boxlite
 * provides no host-side capture of the entrypoint's stdout/stderr, so workload images must
 * write their logs here themselves.
 */
export const GUEST_LOG_DIR = '/var/log/kinotic'

/**
 * How many volumes a workload may declare. Each virtio-fs mount draws from the VM's IRQ
 * budget, and on boxlite 0.9.7 a third one exhausts it — libkrun then fails the VM with
 * {@code RegisterNetDevice(IrqsExhausted)}, reported as {@code status=-22}. Ports and a
 * sized rootfs cost nothing against this. The log directory mount always occupies one of
 * the two the VM can carry.
 */
const MAX_WORKLOAD_VOLUME_MOUNTS = 1

/**
 * The allowlist entry that denies a VM every destination. boxlite 0.9.7 cannot boot a VM
 * with {@code mode: 'disabled'}, and treats an empty allowlist as no allowlist at all, so
 * the only way to deny egress is to permit a single address nothing answers on. TEST-NET-1
 * (RFC 5737) is reserved and never routed, and boxlite's connection filter then refuses
 * every other destination by name and by address alike.
 */
const NO_EGRESS_HOST = '192.0.2.1'

/**
 * The largest rootfs a workload may request. boxlite 0.9.7 grows a box's backing store to
 * about 1071 MiB whatever size was asked for, while reporting the requested size to the
 * guest, so a larger request promises the workload space that does not exist: writes are
 * lost, the file's own size still reports them as written, and the VM often dies outright.
 */
const MAX_WORKLOAD_DISK_MB = 1024

/**
 * Lifecycle handle to a boxlite box, as returned by the runtime's get(). The SDK exports
 * no TS type for it (JsBox), so only the members this provider uses are declared.
 */
export interface BoxHandle {
    start(): Promise<void>
    stop(): Promise<void>
}

/**
 * A VM currently managed by this provider, with the resources that outlive the box itself.
 */
export interface ActiveVm {
    box: BoxHandle
    /** boxlite box id (ULID), assigned once the VM boots. */
    vmId: string
    /** Host directory holding this VM's log files, mounted at {@link GUEST_LOG_DIR}. */
    logDir: string
}

/**
 * Builds the boxlite options for a workload. The given host log directory is always mounted
 * at {@link GUEST_LOG_DIR}; entrypoint and cmd are passed through only when the workload
 * declares them, so an empty value keeps the image default. The workload's disk size caps
 * the guest rootfs, which grows sparsely up to that cap.
 */
export function buildBoxOptions(workload: Workload, logDir: string): SimpleBoxOptions {
    // Silently binding to all interfaces when a specific one was requested would be a
    // security failure, so an unsupported hostIp is rejected outright
    const boundMapping = workload.portMappings.find(mapping => mapping.hostIp)
    if (boundMapping) {
        throw new Error(`boxlite cannot bind a specific host interface (hostIp ${boundMapping.hostIp})`)
    }
    // Silently accepting a size the provider does not honor would hand the workload a disk
    // that swallows writes, so it is refused where the reason can still be reported
    if (workload.diskSizeMb > MAX_WORKLOAD_DISK_MB) {
        throw new Error(`boxlite honors a rootfs up to ${MAX_WORKLOAD_DISK_MB}MB, `
                        + `but ${workload.diskSizeMb}MB was declared`)
    }
    // Rejected here so the operator gets the reason; letting it through surfaces only as
    // an opaque libkrun status=-22 when the VM fails to boot
    if (workload.volumeMounts.length > MAX_WORKLOAD_VOLUME_MOUNTS) {
        throw new Error(`boxlite supports ${MAX_WORKLOAD_VOLUME_MOUNTS} workload volume mount(s) `
                        + `alongside the log mount, but ${workload.volumeMounts.length} were declared`)
    }
    // A workload deserialized from the wire or a persisted state file may predate the
    // network field, whose absence means the policy the model defaults to
    const networkMode = workload.network?.mode ?? NetworkMode.ENABLED
    // Both denials are expressed as an allowlist permitting only {@link NO_EGRESS_HOST}: a
    // disabled network, whose own list is discarded, and an empty list, which boxlite would
    // otherwise read as no filter at all and grant everything. Name resolution runs inside
    // boxlite's network stack rather than over the allowlist, so it survives either.
    const allowedHosts = workload.network?.allowedHosts ?? []
    const allowNet = networkMode === NetworkMode.ENABLED && allowedHosts.length > 0
        ? allowedHosts
        : [NO_EGRESS_HOST]
    return {
        image: workload.image,
        name: workload.id!,
        cpus: workload.vcpus,
        memoryMib: workload.memoryMb,
        // boxlite sizes the rootfs in whole GB; round up so a workload never gets less
        // disk than it asked for, and leave the boxlite default when nothing was asked.
        // MAX_WORKLOAD_DISK_MB keeps this at the one size the provider actually honors
        ...(workload.diskSizeMb > 0 ? { diskSizeGb: Math.ceil(workload.diskSizeMb / 1024) } : {}),
        env: { ...workload.environment, ...workload.secrets },
        // Kubernetes semantics: a declared entrypoint runs exactly as given — the image
        // CMD is suppressed unless the workload declares its own cmd
        ...(workload.entrypoint.length > 0
            ? { entrypoint: workload.entrypoint, cmd: workload.cmd }
            : workload.cmd.length > 0 ? { cmd: workload.cmd } : {}),
        // Always sent rather than left to the boxlite default, so what a guest can reach is
        // decided by the workload record alone. The mode is always 'enabled' because the
        // disabled one cannot boot; every denial is carried by the allowlist instead
        network: {
            mode: 'enabled',
            allowNet,
        },
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
        // boxlite rejects autoRemove on detached boxes, so Workload.autoRemove is
        // implemented by stop() instead of this flag
        autoRemove: false,
        // A workload deserialized from the wire or a persisted state file may predate the
        // detached field; boxlite's default (false) is the opposite of the model's
        detach: workload.detached ?? true,
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
    private readonly boxliteHome: string
    private readonly logsBaseDir: string
    // State must not live under logsBaseDir: log dirs are guest-writable via the
    // GUEST_LOG_DIR mount, and a guest could rewrite its organizationId to reroute
    // its logs to another tenant
    private readonly stateDir: string
    // One boxlite runtime for the whole provider; every SimpleBox is bound to it
    private readonly runtime = getJsBoxlite().withDefaultConfig()
    private readonly onStatusChanged: ((workload: Workload) => void) | null

    constructor(boxliteHome: string,
                logsBaseDir: string,
                stateDir: string,
                onStatusChanged: ((workload: Workload) => void) | null = null) {
        this.boxliteHome = boxliteHome
        this.logsBaseDir = logsBaseDir
        this.stateDir = stateDir
        this.onStatusChanged = onStatusChanged
        mkdirSync(stateDir, { recursive: true })
    }

    async totalDiskMb(): Promise<number> {
        // statfs needs an existing path and boxlite only creates its home on the first box
        mkdirSync(this.boxliteHome, { recursive: true })
        const stats = statfsSync(this.boxliteHome)
        return Math.floor((stats.blocks * stats.bsize) / (1024 * 1024))
    }

    // boxlite carries its own guest kernel and filesystem, so there is nothing on the host
    // it depends on that could quietly stop being true
    async checkNodeHealth(): Promise<string[]> {
        return []
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
        // boxlite cannot observe a guest's exit (the box reports running after the entrypoint
        // ends — see boxlite-test/src/batch-workload-test.ts), so the foreground contract of
        // a non-detached workload — start resolving at run end — cannot be honored
        if (!(workload.detached ?? true)) {
            throw new Error('Non-detached workloads are not supported on the boxlite provider: '
                            + 'boxlite cannot observe a workload\'s exit. Deploy it detached instead.')
        }

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

            // Creates the box record only — the VM does not boot until start()
            const vmId = await new SimpleBox({ ...buildBoxOptions(workload, logDir), runtime: this.runtime }).getId()

            // The runtime's boot handshake doubles as the readiness check; unlike an exec
            // probe it requires no binaries from the guest image
            const box = await this.boxHandle(id)
            await box.start()

            this.activeVms.set(id, { box, vmId, logDir })

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
            const box = await this.boxHandle(workloadId)
            await box.start()

            const logDir = join(this.logsBaseDir, workloadId)
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

        // Implements Workload.autoRemove: boxlite forbids its own autoRemove flag on
        // detached boxes, so the provider discards the box explicitly
        if (workload.autoRemove ?? false) {
            await this.runtime.remove(workloadId, true)
        }

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
                logPath: join(vm.logDir, '*.log'),
                format: LogFormat.PLAIN,
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
                this.activeVms.set(id, { box: await this.boxHandle(id), vmId: info.id, logDir })
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

    // The runtime's own handle to the box named by the workload id
    private async boxHandle(workloadId: string): Promise<BoxHandle> {
        const box: BoxHandle | null = await this.runtime.get(workloadId)
        if (!box) {
            throw new Error(`Box not found for workload: ${workloadId}`)
        }
        return box
    }

    // Written atomically (write + rename) so a crash mid-write cannot corrupt recovery state.
    // Every status transition funnels through here, so the listener sees them all.
    private persist(workload: Workload): void {
        const file = this.stateFile(workload.id!)
        writeFileSync(`${file}.tmp`, JSON.stringify(workload))
        renameSync(`${file}.tmp`, file)
        this.onStatusChanged?.(workload)
    }

    private stateFile(workloadId: string): string {
        return join(this.stateDir, `${workloadId}.json`)
    }
}
