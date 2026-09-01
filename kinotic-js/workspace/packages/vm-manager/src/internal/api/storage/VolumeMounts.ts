import { existsSync, mkdirSync } from 'node:fs'
import { resolve, sep } from 'node:path'
import type { Workload } from '@kinotic-ai/management-api'

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
