import { existsSync, lstatSync, mkdirSync, realpathSync } from 'node:fs'
import { dirname, join, relative, resolve, sep } from 'node:path'
import type { MountQuotaManager } from '@/internal/api/storage/MountQuotaManager'
import type { VolumeMount, Workload } from '@kinotic-ai/management-api'

/**
 * The host side of a workload's volume mounts on one node: where they may live, which of
 * them the node creates, and what they may hold.
 */
export class VolumeMountManager {

    private readonly workloadDataDir: string
    private readonly quotas: MountQuotaManager

    /**
     * @param workloadDataDir base directory every volume mount on this node must live under
     * @param quotas the node's quota manager
     */
    constructor(workloadDataDir: string, quotas: MountQuotaManager) {
        this.workloadDataDir = resolve(workloadDataDir)
        this.quotas = quotas
    }

    /**
     * Validates every volume mount of a workload and creates the missing host directories of
     * the writable ones, leaving each mount ready for a provider to bind into the guest.
     *
     * Every hostPath must resolve strictly inside the node's workload data directory, as written
     * and as the filesystem resolves it through any symlink: mounts are bound with the
     * vm-manager's authority, so an unconstrained path would hand any host directory to the
     * guest. A read-only mount must already exist and fails here naming the
     * path, rather than the provider's runtime refusing the VM or an empty directory
     * appearing at the path.
     *
     * @param workload the workload whose volume mounts are prepared
     */
    public prepare(workload: Workload): void {
        for (const mount of workload.volumeMounts) {
            const hostPath = resolve(mount.hostPath)
            if (!hostPath.startsWith(this.workloadDataDir + sep)) {
                throw new Error(`Volume mount ${mount.hostPath} of workload ${workload.id} must be `
                                + `an absolute path inside the workload data directory ${this.workloadDataDir}`)
            }
            // A directory a workload can write may hold a symlink it planted, which the lexical check passes and
            // both mkdirSync and the provider's bind follow, so the path is compared again as the filesystem
            // resolves it, before anything is created through it
            const dataDir = VolumeMountManager.resolved(this.workloadDataDir)
            const resolvedPath = VolumeMountManager.resolved(hostPath)
            if (!resolvedPath.startsWith(dataDir + sep)) {
                throw new Error(`Volume mount ${mount.hostPath} of workload ${workload.id} resolves to `
                                + `${resolvedPath}, outside the workload data directory ${dataDir}`)
            }
            if (mount.readOnly) {
                if (!existsSync(hostPath)) {
                    throw new Error(`Read-only volume mount ${mount.hostPath} of workload `
                                    + `${workload.id} does not exist on this node`)
                }
            } else {
                mkdirSync(hostPath, { recursive: true })
            }
        }
    }

    // The path with every symlink in the part of it that exists resolved, and the part that does not exist yet
    // appended as written. lstat finds a dangling symlink, which realpath then refuses, where existsSync would
    // report it missing and let mkdirSync create its target.
    private static resolved(path: string): string {
        let existing = path
        while (lstatSync(existing, { throwIfNoEntry: false }) === undefined) {
            existing = dirname(existing)
        }
        return join(realpathSync(existing), relative(existing, path))
    }

    /**
     * Whether a cap declared on a mount of this node can be enforced at all, which the
     * filesystem holding the workload data directory decides.
     */
    public enforcesQuotas(): boolean {
        return this.quotas.supports(this.workloadDataDir)
    }

    /**
     * Fails unless every cap the workload declares can be enforced on this node, so a node
     * provisioned to bound what its workloads write never runs one it cannot bound.
     *
     * @param workload the workload whose declared caps must be enforceable
     */
    public requireEnforceableQuotas(workload: Workload): void {
        for (const mount of this.cappedMounts(workload)) {
            if (!this.quotas.supports(mount.hostPath)) {
                throw new Error(`Cannot cap ${mount.hostPath} of workload ${workload.id}: it is not on `
                                + 'an XFS filesystem mounted with prjquota, which this node requires')
            }
        }
    }

    /**
     * Caps what a workload may write through each of its mounts that declares a size limit.
     * A cap the filesystem holding the mount cannot carry at all leaves that mount uncapped,
     * which is the only way a node without project quotas can run a workload declaring one;
     * a cap that fails to apply anywhere else fails the workload.
     *
     * @param workload the workload whose mounts are capped
     */
    public applyQuotas(workload: Workload): void {
        for (const mount of this.cappedMounts(workload)) {
            if (this.quotas.supports(mount.hostPath)) {
                this.quotas.apply(mount.hostPath, mount.sizeLimitMb!)
            }
        }
    }

    /**
     * Frees the caps a workload's mounts hold, so their project ids can be handed out again.
     *
     * @param workload the workload whose mounts are released
     */
    public releaseQuotas(workload: Workload): void {
        for (const mount of this.cappedMounts(workload)) {
            // Never fails the operation that triggered it: a workload whose quota cannot be
            // released has still been torn down, and a leaked project id costs an id rather
            // than correctness
            try {
                this.quotas.release(mount.hostPath)
            } catch (error) {
                console.error(`Failed to release the quota on ${mount.hostPath}:`, error)
            }
        }
    }

    // A cap bounds what the guest writes, so it is meaningless on a mount the guest cannot write
    private cappedMounts(workload: Workload): VolumeMount[] {
        return workload.volumeMounts.filter(mount => !mount.readOnly
                                                     && mount.sizeLimitMb !== undefined
                                                     && mount.sizeLimitMb > 0)
    }
}
