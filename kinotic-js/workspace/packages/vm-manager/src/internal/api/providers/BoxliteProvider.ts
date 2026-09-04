import { SimpleBox, getJsBoxlite, type Boxlite, type SimpleBoxOptions } from '@boxlite-ai/boxlite'
import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statfsSync } from 'node:fs'
import { join } from 'node:path'
import type { IVmProvider } from '@/internal/api/providers/IVmProvider'
import { Util } from '@/internal/api/Util'
import { MountQuotaManager } from '@/internal/api/storage/MountQuotaManager'
import { VolumeMountManager } from '@/internal/api/storage/VolumeMountManager'
import type { TelemetryTarget } from '@/internal/api/model/TelemetryTarget'
import type { OtlpEndpoint } from '@/internal/api/model/OtlpEndpoint'
import { AlloyManager } from '@/internal/api/telemetry/AlloyManager'
import { LogFormat } from '@/internal/api/model/LogFormat'
import { VmProviderType } from '@kinotic-ai/system-api'
import { LogPolicy, Workload, WorkloadStatus, NetworkMode } from '@kinotic-ai/management-api'

/**
 * Guest path where the per-workload host log directory is mounted. This is the log-shipping
 * contract: log files a workload writes under this directory are shipped to Loki. boxlite
 * provides no host-side capture of the entrypoint's stdout/stderr, so workload images must
 * write their logs here themselves.
 */
export const GUEST_LOG_DIR = '/var/log/kinotic'

/**
 * How many volumes a workload may declare. Each virtio-fs mount draws from the VM's IRQ
 * budget, and on boxlite 0.10.0 a third one exhausts it — libkrun then fails the VM with
 * {@code RegisterNetDevice(IrqsExhausted)}, reported as {@code status=-22}. Ports and a
 * sized rootfs cost nothing against this. The log directory mount always occupies one of
 * the two the VM can carry.
 * @see https://github.com/boxlite-ai/boxlite/issues/935
 */
const MAX_WORKLOAD_VOLUME_MOUNTS = 1

/**
 * The allowlist entry that denies a VM every destination while leaving it a network
 * interface. boxlite reads an empty allowlist as no allowlist at all and grants the VM
 * unrestricted egress, so a policy that allows nothing has to be expressed as permission
 * to a single address nothing answers on. TEST-NET-1 (RFC 5737) is reserved and never
 * routed, and boxlite's connection filter then refuses every other destination by name
 * and by address alike.
 */
const NO_EGRESS_HOST = '192.0.2.1'

/**
 * How often a workload running in the foreground is checked for its end. boxlite's Node SDK
 * offers no wait primitive, so the end of a run is found by polling the box's state.
 */
const EXIT_POLL_MS = 500

/**
 * Where a guest reaches its host: the host alias of the gvisor-tap-vsock network boxlite gives
 * every VM, whose proxy completes the connection over the host's loopback. A guest allowed the
 * alias reaches every loopback service on the node, not one port of it, since boxlite's
 * allowlist matches hosts.
 */
const HOST_FROM_GUEST = '192.168.127.254'

/** Where the node's OTLP receivers for boxlite guests bind, being what the alias lands on. */
const OTLP_LISTEN_ADDRESS = '127.0.0.1'

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
 * VM provider implementation using the boxlite Node.js SDK for micro VM management.
 * Workload state is persisted under the given state directory so a restarted vm-manager
 * can {@link recover} the VMs of a previous process.
 * @see https://github.com/boxlite-ai/boxlite
 */
export class BoxliteProvider implements IVmProvider {

    readonly type: VmProviderType = VmProviderType.BOXLITE

    /**
     * Builds the boxlite options for a workload. The given host log directory is always mounted
     * at {@link GUEST_LOG_DIR}; entrypoint and cmd are passed through only when the workload
     * declares them, so an empty value keeps the image default. The workload's disk size caps
     * the guest rootfs, which grows sparsely up to that cap. A workload that holds an OTLP
     * endpoint is given it in the guest environment, and its host as an allowed destination.
     */
    static buildBoxOptions(workload: Workload, logDir: string, otlp: OtlpEndpoint | null = null): SimpleBoxOptions {
        // Silently binding to all interfaces when a specific one was requested would be a
        // security failure, so an unsupported hostIp is rejected outright
        const boundMapping = workload.portMappings.find(mapping => mapping.hostIp)
        if (boundMapping) {
            throw new Error(`boxlite cannot bind a specific host interface (hostIp ${boundMapping.hostIp})`)
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
        // The node's OTLP endpoint is a destination the workload cannot know, so the node adds
        // it rather than expecting the policy to name it
        const allowedHosts = [...new Set([...(workload.network?.allowedHosts ?? []),
                                          ...(otlp !== null ? [HOST_FROM_GUEST] : [])])]
        const logPolicy = workload.logPolicy ?? new LogPolicy()
        return {
            image: workload.image,
            name: workload.id!,
            cpus: workload.vcpus,
            memoryMib: workload.memoryMb,
            // boxlite sizes the rootfs in whole GB; round up so a workload never gets less
            // disk than it asked for, and leave the boxlite default when nothing was asked
            ...(workload.diskSizeMb > 0 ? { diskSizeGb: Math.ceil(workload.diskSizeMb / 1024) } : {}),
            env: {
                ...AlloyManager.guestEnvironment(workload, HOST_FROM_GUEST, otlp),
                // Nothing captures the entrypoint's stdout, so an image that knows the contract
                // writes its own files under the log mount, rotated by the workload's policy
                KINOTIC_LOG_DIR: GUEST_LOG_DIR,
                KINOTIC_LOG_MAX_SIZE_MB: String(logPolicy.maxSizeMb),
                KINOTIC_LOG_MAX_FILES: String(logPolicy.maxFiles),
            },
            // Kubernetes semantics: a declared entrypoint runs exactly as given — the image
            // CMD is suppressed unless the workload declares its own cmd
            ...(workload.entrypoint.length > 0
                ? { entrypoint: workload.entrypoint, cmd: workload.cmd }
                : workload.cmd.length > 0 ? { cmd: workload.cmd } : {}),
            // Always sent rather than left to the boxlite default, so what a guest can reach is
            // decided by the workload record alone. A disabled network leaves the VM with no
            // interface at all, which is also why boxlite refuses to publish ports on one
            network: {
                outbound: networkMode === NetworkMode.DISABLED
                    ? { mode: 'disabled' }
                    : { mode: 'enabled', allowNet: allowedHosts.length > 0 ? allowedHosts : [NO_EGRESS_HOST] },
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

    private readonly workloads: Map<string, Workload> = new Map()
    private readonly activeVms: Map<string, ActiveVm> = new Map()
    // The box id of every workload with a box on this node, running or not: the vm_id label
    // its shipped logs carry, kept as long as the log files are, until destroy
    private readonly vmIds: Map<string, string> = new Map()
    private readonly boxliteHome: string
    private readonly logsBaseDir: string
    private readonly mounts: VolumeMountManager
    // What the node could enforce when it started, which is what a health check compares
    // against: a node that never had project quotas is not a node that has lost them
    private readonly enforcedQuotasAtStartup: boolean
    // State must not live under logsBaseDir: log dirs are guest-writable via the
    // GUEST_LOG_DIR mount, and a guest could rewrite its organizationId to reroute
    // its logs to another tenant
    private readonly stateDir: string
    // The process-wide boxlite runtime, whose home the executable set from the same
    // configuration this provider is given. It cannot be a per-provider instance: a runtime
    // holds an exclusive lock on its home, and the only way to release one stops every box
    // it is running.
    private readonly runtime: Boxlite = getJsBoxlite().withDefaultConfig()
    private readonly onStatusChanged: ((workload: Workload) => void) | null
    // Null on a node that ships nothing, where a workload's election issues nothing
    private readonly alloyManager: AlloyManager | null

    constructor(boxliteHome: string,
                logsBaseDir: string,
                stateDir: string,
                workloadDataDir: string,
                onStatusChanged: ((workload: Workload) => void) | null = null,
                alloyManager: AlloyManager | null = null) {
        this.boxliteHome = boxliteHome
        this.logsBaseDir = logsBaseDir
        this.stateDir = stateDir
        this.onStatusChanged = onStatusChanged
        this.alloyManager = alloyManager
        mkdirSync(stateDir, { recursive: true })

        this.mounts = new VolumeMountManager(workloadDataDir, new MountQuotaManager())

        // Said once at startup rather than per workload, since a node whose data directory has
        // no project quotas — every macOS node — cannot cap any mount it will ever be given
        this.enforcedQuotasAtStartup = this.mounts.enforcesQuotas()
        if (!this.enforcedQuotasAtStartup) {
            console.warn(`${workloadDataDir} is not on a filesystem with project quotas — a workload `
                         + 'can write past the size limit of a writable mount on this node')
        }
    }

    async totalDiskMb(): Promise<number> {
        // statfs needs an existing path and boxlite only creates its home on the first box
        mkdirSync(this.boxliteHome, { recursive: true })
        const stats = statfsSync(this.boxliteHome)
        return Math.floor((stats.blocks * stats.bsize) / (1024 * 1024))
    }

    // boxlite carries its own guest kernel and filesystem, so the host holds nothing this
    // provider needs except the filesystem a workload's mounts are capped on
    async checkNodeHealth(): Promise<string[]> {
        const problems: string[] = []
        if (this.enforcedQuotasAtStartup && !this.mounts.enforcesQuotas()) {
            problems.push('the workload data directory is no longer on a filesystem with project '
                          + 'quotas, so a workload can write past the size limit of a writable mount')
        }
        return problems
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
        this.alloyManager?.reconcileEndpoints(new Set(this.workloads.keys()))
    }

    async start(workload: Workload): Promise<Workload> {
        const id = workload.id ?? crypto.randomUUID()
        workload.id = id
        workload.status = WorkloadStatus.STARTING
        workload.created = Date.now()
        workload.updated = Date.now()
        workload.exitCode = null

        this.workloads.set(id, workload)

        const logDir = join(this.logsBaseDir, id)
        mkdirSync(logDir, { recursive: true })
        // STARTING is persisted first so a crash mid-boot is visible to recover()
        this.persist(workload)

        try {
            // boxlite refuses a box whose volume host path is missing, so the checkout
            // directory a deployment mounts is created here on its first deployment
            this.mounts.prepare(workload)
            this.mounts.applyQuotas(workload)

            // A box record left by a previous run of this workload would collide on the name
            const stale = await this.runtime.getInfo(id)
            if (stale && !stale.state.running) {
                await this.runtime.remove(id, true)
            }

            // boxlite answers a tag from its cache once it holds one, so a floating tag is
            // resolved here to the digest the registry serves now, which the cache cannot fake
            let image = workload.image
            if (Util.mustPullBeforeStart(image)) {
                image = await Util.pinImageReference(image)
                console.log(`Workload ${id} image ${workload.image} resolved to ${image}`)
            }

            // The guest learns its endpoint from the environment, so it is issued before the box
            const otlp = this.alloyManager?.issueEndpoint(workload, OTLP_LISTEN_ADDRESS) ?? null

            // Creates the box record only — the VM does not boot until start()
            const vmId = await new SimpleBox({ ...BoxliteProvider.buildBoxOptions(workload, logDir, otlp), image, runtime: this.runtime }).getId()
            this.vmIds.set(id, vmId)

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
        if (workload.status !== WorkloadStatus.STOPPED && workload.status !== WorkloadStatus.COMPLETED) {
            throw new Error(`Workload ${workloadId} is neither stopped nor completed (status: ${workload.status})`)
        }
        const info = await this.runtime.getInfo(workloadId)
        if (!info) {
            throw new Error(`Workload ${workloadId} cannot be restarted — its VM was discarded (autoRemove)`)
        }

        workload.status = WorkloadStatus.STARTING
        workload.updated = Date.now()
        workload.exitCode = null
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

    async awaitExit(workloadId: string): Promise<Workload> {
        const workload = this.workloads.get(workloadId)
        if (!workload) {
            throw new Error(`Workload not found: ${workloadId}`)
        }
        await this.watchExit(workload)
        return workload
    }

    async stop(workloadId: string): Promise<void> {
        const workload = this.workloads.get(workloadId)
        if (!workload) {
            throw new Error(`Workload not found: ${workloadId}`)
        }

        // Taken from the runtime rather than activeVms, which a workload that ended on its
        // own has already been dropped from
        const box = await this.boxHandle(workloadId)

        workload.status = WorkloadStatus.STOPPING
        workload.updated = Date.now()
        this.persist(workload)

        await box.stop()

        const info = await this.runtime.getInfo(workloadId)
        workload.exitCode = info ? this.readExitCode(info.id) : null

        // Implements Workload.autoRemove: boxlite forbids its own autoRemove flag on
        // detached boxes, so the provider discards the box explicitly
        if (workload.autoRemove ?? false) {
            await this.runtime.remove(workloadId, true)
            this.mounts.releaseQuotas(workload)
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

        this.mounts.releaseQuotas(workload)
        this.alloyManager?.releaseEndpoint(workloadId)
        rmSync(join(this.logsBaseDir, workloadId), { recursive: true, force: true })
        rmSync(this.stateFile(workloadId), { force: true })
        this.vmIds.delete(workloadId)
        this.workloads.delete(workloadId)
    }

    async getWorkload(workloadId: string): Promise<Workload> {
        const workload = this.workloads.get(workloadId)
        if (!workload) {
            throw new Error(`Workload not found: ${workloadId}`)
        }
        await this.syncStatus(workload)
        return workload
    }

    async listWorkloads(): Promise<Workload[]> {
        const workloads = Array.from(this.workloads.values())
        for (const workload of workloads) {
            await this.syncStatus(workload)
        }
        return workloads
    }

    async listTelemetryTargets(): Promise<TelemetryTarget[]> {
        const ret: TelemetryTarget[] = []
        for (const [workloadId, workload] of this.workloads) {
            const vmId = this.vmIds.get(workloadId)
            const logDir = join(this.logsBaseDir, workloadId)
            if (vmId !== undefined && existsSync(logDir)) {
                ret.push({
                    workloadId,
                    vmId,
                    logPath: join(logDir, '*.log'),
                    format: LogFormat.PLAIN,
                    otlp: this.alloyManager?.endpointOf(workloadId) ?? null,
                    organizationId: workload.organizationId,
                    applicationId: workload.applicationId,
                })
            }
        }
        return ret
    }

    // Reconciles a persisted workload with the actual box state, reattaching when it is
    // still running
    private async recoverVm(workload: Workload): Promise<void> {
        const id = workload.id!
        try {
            const box = await this.runtime.getInfo(id)
            if (box) {
                this.vmIds.set(id, box.id)
            }
        } catch (error) {
            console.error(`Failed to look up the box of workload ${id}:`, error)
        }
        if (workload.status !== WorkloadStatus.STARTING &&
            workload.status !== WorkloadStatus.RUNNING &&
            workload.status !== WorkloadStatus.STOPPING) {
            return
        }
        try {
            const info = await this.runtime.getInfo(id)
            if (info?.state.running) {
                const logDir = join(this.logsBaseDir, id)
                this.activeVms.set(id, { box: await this.boxHandle(id), vmId: info.id, logDir })
                workload.status = WorkloadStatus.RUNNING
                console.log(`Reattached to running workload ${id} (vm ${info.id})`)
            } else {
                // The box ended while no vm-manager was supervising it
                workload.exitCode = info ? this.readExitCode(info.id) : null
                workload.status = this.exitedStatus(workload, workload.exitCode)
            }
        } catch (error) {
            console.error(`Failed to reattach workload ${id}:`, error)
            workload.status = WorkloadStatus.FAILED
        }
        workload.updated = Date.now()
        this.persist(workload)
    }

    // Refreshes a workload the vm-manager believes is live. A box stops when its entrypoint
    // exits, so a guest that ended on its own is reported here rather than waiting for an
    // operation against it to fail.
    private async syncStatus(workload: Workload): Promise<void> {
        if (workload.status !== WorkloadStatus.RUNNING && workload.status !== WorkloadStatus.STARTING) {
            return
        }
        let status: WorkloadStatus
        let exitCode: number | null = workload.exitCode
        try {
            const info = await this.runtime.getInfo(workload.id!)
            if (info?.state.running) {
                status = WorkloadStatus.RUNNING
            } else {
                exitCode = info ? this.readExitCode(info.id) : null
                status = this.exitedStatus(workload, exitCode)
            }
        } catch {
            // The box is gone while the workload record says it should be live
            status = WorkloadStatus.FAILED
        }
        // destroy() can tear the workload down while getInfo is in flight; persisting here
        // would resurrect the state file it removed
        if (!this.workloads.has(workload.id!)) {
            return
        }
        if (status !== workload.status || exitCode !== workload.exitCode) {
            workload.status = status
            workload.exitCode = exitCode
            workload.updated = Date.now()
            // The log dir is kept so already-written logs remain shippable until destroy
            this.activeVms.delete(workload.id!)
            this.persist(workload)
        }
    }

    // Pushes the run's end the moment the guest exits, instead of leaving it for the next
    // listWorkloads() sweep to notice. Started only for a foreground run, whose start awaits
    // it: a detached workload would hold a poll for its whole life to learn what the sweep
    // tells it anyway.
    private async watchExit(workload: Workload): Promise<void> {
        while ((await this.runtime.getInfo(workload.id!))?.state.running) {
            await new Promise(resolve => setTimeout(resolve, EXIT_POLL_MS))
        }
        await this.syncStatus(workload)
    }

    // A guest that ended while no operation was in flight ended cleanly only if it said so;
    // anything else — including an exit whose code could not be read — is a failure. A clean
    // end is what a foreground run is for, so it completed; a detached service merely stopped
    private exitedStatus(workload: Workload, exitCode: number | null): WorkloadStatus {
        let ret: WorkloadStatus
        if (workload.status === WorkloadStatus.STOPPING) {
            ret = WorkloadStatus.STOPPED
        } else if (exitCode !== 0) {
            ret = WorkloadStatus.FAILED
        } else if (workload.detached ?? true) {
            ret = WorkloadStatus.STOPPED
        } else {
            ret = WorkloadStatus.COMPLETED
        }
        return ret
    }

    /**
     * The exit code the guest recorded for a box's run, or null when no record can be read.
     */
    // boxlite's Node SDK drops the exit code its core records (boxlite PR #1237), so it is
    // read from the record the guest writes beside the container's rootfs. A box carries one
    // container, and the record is per-run: Container.Init removes it before starting, so its
    // presence means this run is over. Absent or unreadable is an unknown outcome, which
    // exitedStatus must not read as success.
    private readExitCode(vmId: string): number | null {
        const containers = join(this.boxliteHome, 'boxes', vmId, 'shared', 'containers')
        let ret: number | null = null
        try {
            const [containerId] = readdirSync(containers)
            if (containerId !== undefined) {
                const record = JSON.parse(readFileSync(join(containers, containerId, 'exit.json'), 'utf-8'))
                if (typeof record.exit_code === 'number') {
                    ret = record.exit_code
                }
            }
        } catch {
            // No readable record: the run ended, but not with an outcome this can report
        }
        return ret
    }

    // The runtime's own handle to the box named by the workload id
    private async boxHandle(workloadId: string): Promise<BoxHandle> {
        const box: BoxHandle | null = await this.runtime.get(workloadId)
        if (!box) {
            throw new Error(`Box not found for workload: ${workloadId}`)
        }
        return box
    }

    // Every status transition funnels through here, so the listener sees them all
    private persist(workload: Workload): void {
        Util.writeJsonAtomically(this.stateFile(workload.id!), workload)
        this.onStatusChanged?.(workload)
    }

    private stateFile(workloadId: string): string {
        return join(this.stateDir, `${workloadId}.json`)
    }
}
