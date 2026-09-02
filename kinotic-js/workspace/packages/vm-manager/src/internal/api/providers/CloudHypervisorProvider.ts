import Docker from 'dockerode'
import type { ContainerCreateOptions, ContainerInspectInfo } from 'dockerode'
import { mkdirSync, readdirSync, readFileSync, renameSync, rmSync, statfsSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import type { IVmProvider } from '@/internal/api/providers/IVmProvider'
import { MountQuotaManager } from '@/internal/api/storage/MountQuotaManager'
import { VolumeMountManager } from '@/internal/api/storage/VolumeMountManager'
import { EgressPolicyManager } from '@/internal/api/network/EgressPolicyManager'
import type { LogTarget } from '@/model/LogTarget'
import { LogFormat } from '@/model/LogFormat'
import { VmProviderType } from '@kinotic-ai/system-api'
import { Workload, WorkloadStatus, NetworkMode, PortProtocol } from '@kinotic-ai/management-api'

/**
 * The Docker runtime that boots each container as a Cloud Hypervisor micro VM. Registered in
 * the node's daemon.json by the provisioning that makes a node a CLOUD_HYPERVISOR node, so it
 * is the same name on every such node rather than a per-environment setting.
 */
export const KATA_CLH_RUNTIME = 'kata-clh'

/** Carries the workload id on its container, which is how {@link CloudHypervisorProvider#recover} finds it. */
const WORKLOAD_LABEL = 'ai.kinotic.workload'

/** Distinguishes this node's workload containers from anything else running on the daemon. */
const MANAGED_BY_LABEL = 'ai.kinotic.managed-by'
const MANAGED_BY = 'kinotic-vm-manager'

// Grace period before the guest is killed, matching the boxlite provider's stop behaviour
const STOP_TIMEOUT_SECONDS = 10

/**
 * A container currently managed by this provider.
 */
export interface ActiveContainer {
    /** Docker's id for the container, which is also the micro VM's identity on the node. */
    containerId: string
    /** Host path of the json-file the daemon captures the workload's stdout and stderr to. */
    logPath: string
}

/**
 * VM provider that runs each workload as a Cloud Hypervisor micro VM, driving the Docker
 * Engine API with the Kata runtime. Workload state is persisted under the given state
 * directory so a restarted vm-manager can {@link recover} the VMs of a previous process.
 *
 * Requires a node provisioned for it: the {@link KATA_CLH_RUNTIME} runtime registered with
 * the daemon, and a data root on XFS with project quotas so a workload's rootfs and its
 * writable mounts can both be capped.
 */
export class CloudHypervisorProvider implements IVmProvider {

    readonly type: VmProviderType = VmProviderType.CLOUD_HYPERVISOR

    private readonly workloads: Map<string, Workload> = new Map()
    private readonly containers: Map<string, ActiveContainer> = new Map()
    private readonly stateDir: string
    private readonly docker: Docker
    private readonly workloadDataDir: string
    private readonly quotas = new MountQuotaManager()
    private readonly mounts: VolumeMountManager
    private readonly egress: EgressPolicyManager
    private readonly resolver: string | null
    private readonly onStatusChanged: ((workload: Workload) => void) | null

    constructor(stateDir: string,
                docker: Docker,
                workloadDataDir: string,
                egress: EgressPolicyManager = new EgressPolicyManager(),
                resolver: string | null = null,
                onStatusChanged: ((workload: Workload) => void) | null = null) {
        this.stateDir = stateDir
        this.docker = docker
        this.workloadDataDir = resolve(workloadDataDir)
        this.egress = egress
        this.resolver = resolver
        this.onStatusChanged = onStatusChanged
        mkdirSync(stateDir, { recursive: true })
        mkdirSync(this.workloadDataDir, { recursive: true })
        this.mounts = VolumeMountManager.requiringQuotas(this.workloadDataDir, this.quotas)
    }

    /**
     * Builds the Docker create options for a workload. Entrypoint and cmd follow Kubernetes
     * semantics: a declared entrypoint runs exactly as given, suppressing the image CMD unless
     * the workload declares its own.
     */
    private buildCreateOptions(workload: Workload): ContainerCreateOptions {
        // A workload deserialized from the wire or a persisted state file may predate the
        // network field, whose absence means the policy the model defaults to
        const networkMode = workload.network?.mode ?? NetworkMode.ENABLED
        const logPolicy = workload.logPolicy ?? { maxSizeMb: 10, maxFiles: 3 }

        const exposedPorts: Record<string, Record<string, never>> = {}
        const portBindings: Record<string, Array<{ HostIp?: string, HostPort?: string }>> = {}
        for (const { hostPort, guestPort, protocol, hostIp } of workload.portMappings) {
            const port = `${guestPort}/${(protocol ?? PortProtocol.TCP).toLowerCase()}`
            exposedPorts[port] = {}
            portBindings[port] = [{
                ...(hostIp !== undefined ? { HostIp: hostIp } : {}),
                ...(hostPort !== undefined ? { HostPort: String(hostPort) } : {}),
            }]
        }

        return {
            name: workload.id!,
            Image: workload.image,
            Labels: { [WORKLOAD_LABEL]: workload.id!, [MANAGED_BY_LABEL]: MANAGED_BY },
            Env: Object.entries({ ...workload.environment, ...workload.secrets })
                       .map(([key, value]) => `${key}=${value}`),
            ...(workload.entrypoint.length > 0
                ? { Entrypoint: workload.entrypoint, Cmd: workload.cmd }
                : workload.cmd.length > 0 ? { Cmd: workload.cmd } : {}),
            ExposedPorts: exposedPorts,
            HostConfig: {
                Runtime: KATA_CLH_RUNTIME,
                Memory: workload.memoryMb * 1024 * 1024,
                NanoCpus: workload.vcpus * 1_000_000_000,
                // Needs overlay2 on an XFS filesystem mounted with pquota, which the node's
                // provisioning supplies; without it the daemon refuses the container outright
                ...(workload.diskSizeMb > 0 ? { StorageOpt: { size: `${workload.diskSizeMb}m` } } : {}),
                Binds: workload.volumeMounts.map(({ hostPath, guestPath, readOnly }) =>
                    `${hostPath}:${guestPath}:${readOnly ? 'ro' : 'rw'}`),
                LogConfig: {
                    Type: 'json-file',
                    Config: {
                        'max-size': `${logPolicy.maxSizeMb}m`,
                        // Docker counts the current file among its max-file, the policy counts
                        // only the rotated ones kept beside it
                        'max-file': String(logPolicy.maxFiles + 1),
                    },
                },
                NetworkMode: networkMode === NetworkMode.DISABLED ? 'none' : 'bridge',
                // Pinned so the resolver the egress rules permit is the one the guest is given,
                // rather than whatever the daemon happened to inject
                ...(this.resolver !== null ? { Dns: [this.resolver] } : {}),
                PortBindings: portBindings,
                // The vm-manager owns restarts, so the daemon must not resurrect a workload
                // behind its back and leave the server's record stale
                RestartPolicy: { Name: 'no' },
            },
        }
    }

    async checkNodeHealth(): Promise<string[]> {
        const problems: string[] = []
        let dataRoot: string | null = null
        try {
            dataRoot = (await this.docker.info()).DockerRootDir
        } catch (error) {
            problems.push(`the container runtime is not answering: ${(error as Error).message}`)
        }
        if (dataRoot !== null && !this.quotas.supports(dataRoot)) {
            problems.push(`${dataRoot} is not on a filesystem with project quotas, `
                          + 'so a workload can write past the disk size it was given')
        }
        if (!this.egress.blocksCloudMetadata()) {
            problems.push('the node firewall does not block the cloud metadata endpoint, '
                          + "so a workload can read this host's credentials")
        }
        if (!this.quotas.supports(this.workloadDataDir)) {
            problems.push(`${this.workloadDataDir} is not on a filesystem with project quotas, `
                          + 'so a workload can write past the size limit of a writable mount')
        }
        return problems
    }

    async totalDiskMb(): Promise<number> {
        // Every container's rootfs comes out of the daemon's data root, wherever the node
        // put it — commonly a filesystem of its own, sized differently from the host's
        const stats = statfsSync((await this.docker.info()).DockerRootDir)
        return Math.floor((stats.blocks * stats.bsize) / (1024 * 1024))
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
            await this.recoverContainer(workload)
        }
        this.egress.reconcile(new Set(this.containers.keys()))
    }

    async start(workload: Workload): Promise<Workload> {
        const id = workload.id ?? crypto.randomUUID()
        workload.id = id
        workload.status = WorkloadStatus.STARTING
        workload.created = Date.now()
        workload.updated = Date.now()
        workload.exitCode = null

        this.workloads.set(id, workload)
        // STARTING is persisted first so a crash mid-boot is visible to recover()
        this.persist(workload)

        let exitWatch: Promise<void> = Promise.resolve()
        try {
            this.mounts.prepare(workload)
            this.mounts.applyQuotas(workload)
            await this.ensureImage(workload.image)
            // A container left by a previous run of this workload would collide on the name
            await this.removeContainer(id)

            const container = await this.docker.createContainer(this.buildCreateOptions(workload))
            await container.start()

            const info = await container.inspect()
            this.applyEgressPolicy(workload, info)
            this.containers.set(id, { containerId: info.Id, logPath: info.LogPath })

            workload.status = WorkloadStatus.RUNNING
            exitWatch = this.watchExit(workload)
        } catch (error) {
            workload.status = WorkloadStatus.FAILED
            this.containers.delete(id)
            throw error
        } finally {
            workload.updated = Date.now()
            this.persist(workload)
        }

        // A non-detached workload runs in the foreground: start resolves only once the run
        // has ended, with the outcome recorded on the workload by the exit watch
        if (!(workload.detached ?? true)) {
            await exitWatch
        }

        return workload
    }

    async restart(workloadId: string): Promise<Workload> {
        const workload = this.requireWorkload(workloadId)
        if (workload.status !== WorkloadStatus.STOPPED) {
            throw new Error(`Workload ${workloadId} is not stopped (status: ${workload.status})`)
        }

        workload.status = WorkloadStatus.STARTING
        workload.updated = Date.now()
        workload.exitCode = null
        this.persist(workload)

        let exitWatch: Promise<void> = Promise.resolve()
        try {
            // Starting the stopped container again keeps its writable layer, so the workload
            // resumes with the disk state it had
            const container = this.docker.getContainer(workloadId)
            await container.start()

            const info = await container.inspect()
            this.applyEgressPolicy(workload, info)
            this.containers.set(workloadId, { containerId: info.Id, logPath: info.LogPath })

            workload.status = WorkloadStatus.RUNNING
            exitWatch = this.watchExit(workload)
        } catch (error) {
            workload.status = WorkloadStatus.FAILED
            this.containers.delete(workloadId)
            throw error
        } finally {
            workload.updated = Date.now()
            this.persist(workload)
        }

        // A non-detached workload runs in the foreground: restart resolves only once the run
        // has ended, with the outcome recorded on the workload by the exit watch
        if (!(workload.detached ?? true)) {
            await exitWatch
        }

        return workload
    }

    async stop(workloadId: string): Promise<void> {
        const workload = this.requireWorkload(workloadId)

        workload.status = WorkloadStatus.STOPPING
        workload.updated = Date.now()
        this.persist(workload)

        const container = this.docker.getContainer(workloadId)
        await container.stop({ t: STOP_TIMEOUT_SECONDS })
        workload.exitCode = (await container.inspect()).State.ExitCode
        this.egress.release(workloadId)

        // Implements Workload.autoRemove: the container is created without Docker's own
        // auto-remove so that a stopped workload can be restarted when the flag is off
        if (workload.autoRemove ?? false) {
            await this.removeContainer(workloadId)
            this.mounts.releaseQuotas(workload)
        }

        workload.status = WorkloadStatus.STOPPED
        workload.updated = Date.now()
        this.containers.delete(workloadId)
        this.persist(workload)
    }

    async destroy(workloadId: string): Promise<void> {
        const workload = this.requireWorkload(workloadId)

        await this.removeContainer(workloadId)
        this.mounts.releaseQuotas(workload)
        this.egress.release(workloadId)

        this.containers.delete(workloadId)
        rmSync(this.stateFile(workloadId), { force: true })
        this.workloads.delete(workloadId)
    }

    async getWorkload(workloadId: string): Promise<Workload> {
        const workload = this.requireWorkload(workloadId)
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

    async listLogTargets(): Promise<LogTarget[]> {
        return Array.from(this.containers.entries()).map(([workloadId, container]) => {
            const workload = this.workloads.get(workloadId)!
            return {
                workloadId,
                vmId: container.containerId,
                logPath: container.logPath,
                format: LogFormat.DOCKER_JSON,
                organizationId: workload.organizationId,
                applicationId: workload.applicationId,
            }
        })
    }

    // Reconciles a persisted workload with the actual container state, reattaching when it is
    // still running
    private async recoverContainer(workload: Workload): Promise<void> {
        if (workload.status !== WorkloadStatus.STARTING &&
            workload.status !== WorkloadStatus.RUNNING &&
            workload.status !== WorkloadStatus.STOPPING) {
            return
        }
        const id = workload.id!
        try {
            const info = await this.docker.getContainer(id).inspect()
            if (info.State.Running) {
                this.containers.set(id, { containerId: info.Id, logPath: info.LogPath })
                workload.status = WorkloadStatus.RUNNING
                this.watchExit(workload)
                console.log(`Reattached to running workload ${id} (container ${info.Id.slice(0, 12)})`)
            } else {
                workload.status = this.exitedStatus(workload, info)
                workload.exitCode = info.State.ExitCode
            }
        } catch (error) {
            console.error(`Failed to reattach workload ${id}:`, error)
            workload.status = WorkloadStatus.FAILED
        }
        workload.updated = Date.now()
        this.persist(workload)
    }

    // Refreshes a workload the vm-manager believes is live, so a guest that died on its own
    // is reported rather than waiting for an operation against it
    private async syncStatus(workload: Workload): Promise<void> {
        if (workload.status !== WorkloadStatus.RUNNING && workload.status !== WorkloadStatus.STARTING) {
            return
        }
        let status: WorkloadStatus
        let exitCode: number | null = workload.exitCode
        try {
            const info = await this.docker.getContainer(workload.id!).inspect()
            if (info.State.Running) {
                status = WorkloadStatus.RUNNING
            } else {
                status = this.exitedStatus(workload, info)
                exitCode = info.State.ExitCode
            }
        } catch {
            // The container is gone while the workload record says it should be live
            status = WorkloadStatus.FAILED
        }
        // destroy() can tear the workload down while inspect is in flight; persisting here
        // would resurrect the state file it removed
        if (!this.workloads.has(workload.id!)) {
            return
        }
        if (status !== workload.status || exitCode !== workload.exitCode) {
            workload.status = status
            workload.exitCode = exitCode
            workload.updated = Date.now()
            this.egress.release(workload.id!)
            this.containers.delete(workload.id!)
            this.persist(workload)
        }
    }

    // Pushes the run's end the moment the guest exits, instead of leaving it for the next
    // heartbeat's listWorkloads() sweep to notice. syncStatus() no-ops for exits another
    // operation (stop, destroy) is already handling. The returned promise settles once the
    // exit is recorded, which is what a non-detached start awaits.
    private watchExit(workload: Workload): Promise<void> {
        return this.docker.getContainer(workload.id!).wait()
            .then(() => this.syncStatus(workload))
            .catch(() => {
                // The container was removed or the daemon restarted — the heartbeat sweep reconciles
            })
    }

    // A guest that ended while no operation was in flight stopped cleanly only if it said so;
    // anything else — including the 137 a memory limit produces — is a failure
    private exitedStatus(workload: Workload, info: ContainerInspectInfo): WorkloadStatus {
        let ret: WorkloadStatus
        if (workload.status === WorkloadStatus.STOPPING) {
            ret = WorkloadStatus.STOPPED
        } else if (info.State.ExitCode === 0) {
            ret = WorkloadStatus.STOPPED
        } else {
            ret = WorkloadStatus.FAILED
        }
        return ret
    }

    /**
     * Pulls the image unless the daemon already has it, which keeps a node able to run images
     * built on it and keeps a restart off the network.
     */
    private async ensureImage(image: string): Promise<void> {
        const local = await this.docker.listImages({ filters: { reference: [image] } })
        if (local.length > 0) {
            return
        }
        const stream = await this.docker.pull(image)
        await new Promise<void>((resolve, reject) => {
            this.docker.modem.followProgress(stream, (error: Error | null) =>
                error ? reject(error) : resolve())
        })
    }

    /**
     * Restricts what the workload's micro VM may reach. A node that does not deny by default
     * cannot honour a declared allowlist, and saying so is better than writing rules that
     * permit access nothing was withholding.
     */
    private applyEgressPolicy(workload: Workload, info: ContainerInspectInfo): void {
        const allowedHosts = workload.network?.allowedHosts ?? []
        if (!this.egress.enforces()) {
            if (allowedHosts.length > 0) {
                throw new Error(`Workload ${workload.id} declares ${allowedHosts.length} allowedHosts, `
                                + 'but this node does not deny workload egress by default')
            }
            return
        }
        // A workload with no network has no address and nothing to restrict
        const address = info.NetworkSettings?.Networks?.bridge?.IPAddress
        if (address) {
            this.egress.apply(workload.id!, address, allowedHosts)
        }
    }

    // Removing a container that is not there is the state the caller wanted
    private async removeContainer(workloadId: string): Promise<void> {
        try {
            await this.docker.getContainer(workloadId).remove({ force: true, v: true })
        } catch (error) {
            if ((error as { statusCode?: number }).statusCode !== 404) {
                throw error
            }
        }
    }

    private requireWorkload(workloadId: string): Workload {
        const workload = this.workloads.get(workloadId)
        if (!workload) {
            throw new Error(`Workload not found: ${workloadId}`)
        }
        return workload
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
