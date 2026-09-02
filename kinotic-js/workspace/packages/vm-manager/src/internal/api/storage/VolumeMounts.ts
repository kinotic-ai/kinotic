import { existsSync, mkdirSync } from 'node:fs'
import { resolve, sep } from 'node:path'
import type { MountQuotaManager } from '@/internal/api/storage/MountQuotaManager'
import type { VolumeMount, Workload } from '@kinotic-ai/management-api'

/**
 * Validates every volume mount of a workload and creates the missing host directories of the
 * writable ones, leaving each mount ready for a provider to bind into the guest.
 *
 * Every hostPath must resolve strictly inside the node's workload data directory: mounts are
 * bound with the vm-manager's authority, so an unconstrained path would hand any host
 * directory to the guest. A read-only mount must already exist and fails here naming the
 * path, rather than the provider's runtime refusing the VM or an empty directory appearing
 * at the path.
 *
 * @param workload the workload whose volume mounts are prepared
 * @param workloadDataDir base directory every volume mount on this node must live under
 */
export function prepareVolumeMounts(workload: Workload, workloadDataDir: string): void {
    const baseDir = resolve(workloadDataDir)
    for (const mount of workload.volumeMounts) {
        const hostPath = resolve(mount.hostPath)
        if (!hostPath.startsWith(baseDir + sep)) {
            throw new Error(`Volume mount ${mount.hostPath} of workload ${workload.id} must be `
                            + `an absolute path inside the workload data directory ${baseDir}`)
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
 * Caps what a workload may write through each of its mounts that declares a size limit, and
 * fails when a declared cap cannot be applied — a workload that asked to be bounded must not
 * run unbounded.
 *
 * @param workload the workload whose mounts are capped
 * @param quotas the node's quota manager, or null on a node that enforces no caps
 */
export function applyMountQuotas(workload: Workload, quotas: MountQuotaManager | null): void {
    if (quotas === null) {
        return
    }
    for (const mount of cappedMounts(workload)) {
        quotas.apply(mount.hostPath, mount.sizeLimitMb!)
    }
}

/**
 * Frees the caps a workload's mounts hold, so their project ids can be handed out again.
 *
 * @param workload the workload whose mounts are released
 * @param quotas the node's quota manager, or null on a node that enforces no caps
 */
export function releaseMountQuotas(workload: Workload, quotas: MountQuotaManager | null): void {
    if (quotas === null) {
        return
    }
    for (const mount of cappedMounts(workload)) {
        // Never fails the operation that triggered it: a workload whose quota cannot be
        // released has still been torn down, and a leaked project id costs an id rather
        // than correctness
        try {
            quotas.release(mount.hostPath)
        } catch (error) {
            console.error(`Failed to release the quota on ${mount.hostPath}:`, error)
        }
    }
}

// A cap bounds what the guest writes, so it is meaningless on a mount the guest cannot write
function cappedMounts(workload: Workload): VolumeMount[] {
    return workload.volumeMounts.filter(mount => !mount.readOnly
                                                 && mount.sizeLimitMb !== undefined
                                                 && mount.sizeLimitMb > 0)
}
