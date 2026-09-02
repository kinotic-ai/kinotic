import { existsSync, mkdirSync } from 'node:fs'
import { resolve, sep } from 'node:path'
import type { MountQuotaManager } from '@/internal/api/storage/MountQuotaManager'
import type { VolumeMount, Workload } from '@kinotic-ai/management-api'

/**
 * The host side of a workload's volume mounts on one node: where they may live, which of
 * them the node creates, and what they may hold.
 */
export class VolumeMountManager {

    private readonly workloadDataDir: string
    private readonly quotas: MountQuotaManager
    private readonly quotasRequired: boolean

    private constructor(workloadDataDir: string, quotas: MountQuotaManager, quotasRequired: boolean) {
        this.workloadDataDir = resolve(workloadDataDir)
        this.quotas = quotas
        this.quotasRequired = quotasRequired
    }

    /**
     * Mounts on a node whose filesystem is provisioned to carry project quotas: a declared
     * cap that cannot be applied fails the workload, since a node that is meant to bound what
     * a workload writes must not run one it cannot bound.
     *
     * @param workloadDataDir base directory every volume mount on this node must live under
     * @param quotas the node's quota manager
     */
    public static requiringQuotas(workloadDataDir: string, quotas: MountQuotaManager): VolumeMountManager {
        return new VolumeMountManager(workloadDataDir, quotas, true)
    }

    /**
     * Mounts on a node that may carry no project quotas at all, such as a developer's macOS
     * machine. A declared cap is enforced wherever the filesystem holding the mount can carry
     * it, and only a mount on a filesystem that cannot runs uncapped.
     *
     * @param workloadDataDir base directory every volume mount on this node must live under
     * @param quotas the node's quota manager
     */
    public static usingAvailableQuotas(workloadDataDir: string, quotas: MountQuotaManager): VolumeMountManager {
        return new VolumeMountManager(workloadDataDir, quotas, false)
    }

    /**
     * Validates every volume mount of a workload and creates the missing host directories of
     * the writable ones, leaving each mount ready for a provider to bind into the guest.
     *
     * Every hostPath must resolve strictly inside the node's workload data directory: mounts
     * are bound with the vm-manager's authority, so an unconstrained path would hand any host
     * directory to the guest. A read-only mount must already exist and fails here naming the
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

    /**
     * Caps what a workload may write through each of its mounts that declares a size limit.
     * Whether a cap this node cannot apply fails the workload is what the manager was created
     * with.
     *
     * @param workload the workload whose mounts are capped
     */
    public applyQuotas(workload: Workload): void {
        for (const mount of this.cappedMounts(workload)) {
            // apply() throws when the filesystem cannot carry the cap, which is what fails the
            // workload on a node that must enforce one; elsewhere the mount is left uncapped
            if (this.quotasRequired || this.quotas.supports(mount.hostPath)) {
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
