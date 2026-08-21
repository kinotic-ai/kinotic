import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

// Project ids below this are left to whatever else uses the filesystem; workload quotas are
// allocated upward from here so a hand-assigned project is never overwritten.
const FIRST_WORKLOAD_PROJECT_ID = 10_000

/**
 * A directory's quota as the filesystem reports it.
 */
export interface MountQuota {
    /** The XFS project id charged for writes under the directory. */
    projectId: number
    /** Bytes currently charged to the project. */
    usedBytes: number
    /** The hard cap, or 0 when the project has no cap. */
    limitBytes: number
}

/**
 * Caps how much a workload may write through a volume mount, using XFS project quotas.
 *
 * cgroups do not bound storage, so a workload's memory limit says nothing about how much it
 * can write. A project quota is charged against a directory tree and inherited by everything
 * created under it, which is what makes it enforceable against code the platform does not
 * trust: the guest writes through the mount and the host filesystem refuses once the cap is
 * reached, whatever the workload does.
 *
 * The quota lives on the filesystem rather than in this process, so the project id assigned
 * to a directory can be read back after a restart and nothing needs persisting here.
 *
 * Requires the directory to be on an XFS filesystem mounted with the `prjquota` option, and
 * the process to be root. {@link supports} reports whether a path qualifies.
 */
export class MountQuotaManager {

    /**
     * Whether a hard cap can be applied to the given directory: it is on an XFS filesystem
     * mounted with project quotas enabled, and the tools to manage them are present.
     */
    public supports(directory: string): boolean {
        return this.quotaMountPoint(directory) !== null && this.hasXfsTools()
    }

    /**
     * Caps writes under the given directory at the given size and returns the project id the
     * cap was assigned to. The directory must already exist, since the project id is applied
     * to it and inherited by everything created inside it afterwards. Re-applying to a
     * directory that already has a cap replaces the limit and keeps its project id.
     *
     * @param directory the directory to cap, on a project-quota filesystem
     * @param sizeLimitMb the hard cap in megabytes
     * @return the project id charged for the directory
     */
    public apply(directory: string, sizeLimitMb: number): number {
        const path = resolve(directory)
        const mountPoint = this.requireQuotaMountPoint(path)
        if (sizeLimitMb <= 0) {
            throw new Error(`A mount quota must be a positive size, got ${sizeLimitMb}MB for ${path}`)
        }

        // Reuse the id already on the directory so re-applying does not leak project ids, and
        // so a workload recovered after a restart keeps the accounting it was started with
        const existing = this.projectIdOf(path)
        const projectId = existing !== 0 ? existing : this.nextFreeProjectId(mountPoint)

        if (existing === 0) {
            this.runQuota(mountPoint, `project -s -p ${path} ${projectId}`,
                          `assign project ${projectId} to ${path}`)
        }
        this.runQuota(mountPoint, `limit -p bhard=${sizeLimitMb}m ${projectId}`,
                      `cap project ${projectId} at ${sizeLimitMb}MB`)
        return projectId
    }

    /**
     * Removes the cap from the given directory, freeing its project id for reuse. Does nothing
     * when the directory has no cap, so it is safe to call for a workload that declared none.
     */
    public release(directory: string): void {
        const path = resolve(directory)
        const mountPoint = this.quotaMountPoint(path)
        if (mountPoint === null) {
            return
        }
        const projectId = this.projectIdOf(path)
        if (projectId === 0) {
            return
        }
        // Clearing the limit is what frees the id: an id with no limit is not reported as in
        // use, so nextFreeProjectId can hand it out again
        this.runQuota(mountPoint, `limit -p bhard=0 ${projectId}`, `clear the cap on project ${projectId}`)
        this.runQuota(mountPoint, `project -C -p ${path} ${projectId}`, `unassign project ${projectId} from ${path}`)
    }

    /**
     * What the filesystem is charging the given directory, or null when it has no cap.
     */
    public quotaOf(directory: string): MountQuota | null {
        const path = resolve(directory)
        const mountPoint = this.quotaMountPoint(path)
        if (mountPoint === null) {
            return null
        }
        const projectId = this.projectIdOf(path)
        if (projectId === 0) {
            return null
        }
        const report = this.runQuota(mountPoint, 'report -p -N -b', 'read the quota report')
        // Report rows are: #<id> <used> <soft> <hard> <warn> <grace>, in 1KB blocks
        const row = report.split('\n')
            .map(line => line.trim().split(/\s+/))
            .find(fields => fields[0] === `#${projectId}`)
        let ret: MountQuota | null = null
        if (row !== undefined) {
            ret = {
                projectId,
                usedBytes: Number(row[1] ?? 0) * 1024,
                limitBytes: Number(row[3] ?? 0) * 1024,
            }
        }
        return ret
    }

    /**
     * The mount point of the project-quota filesystem holding the path, or null when the path
     * is not on one.
     */
    private quotaMountPoint(path: string): string | null {
        const target = resolve(path)
        let ret: string | null = null
        let longest = -1
        for (const line of this.mountTable().split('\n')) {
            const [, mountPoint, fsType, options] = line.split(/\s+/)
            if (mountPoint === undefined || fsType !== 'xfs' || !options?.split(',').includes('prjquota')) {
                continue
            }
            // Longest matching mount point wins, so a nested mount is preferred over its parent
            const prefix = mountPoint.endsWith('/') ? mountPoint : `${mountPoint}/`
            if ((target === mountPoint || target.startsWith(prefix)) && mountPoint.length > longest) {
                longest = mountPoint.length
                ret = mountPoint
            }
        }
        return ret
    }

    /**
     * The kernel's mount table, or empty when the host does not publish one. A host without
     * /proc/mounts has no project-quota filesystem to find, so callers see "not supported"
     * rather than a read error from a path they never asked about.
     */
    private mountTable(): string {
        let ret = ''
        try {
            ret = readFileSync('/proc/mounts', 'utf-8')
        } catch {
            ret = ''
        }
        return ret
    }

    private requireQuotaMountPoint(path: string): string {
        const mountPoint = this.quotaMountPoint(path)
        if (mountPoint === null) {
            throw new Error(`Cannot cap ${path}: it is not on an XFS filesystem mounted with prjquota. `
                            + `A workload declaring a mount sizeLimitMb needs one on this node.`)
        }
        if (!this.hasXfsTools()) {
            throw new Error(`Cannot cap ${path}: xfs_quota and xfs_io are required (install xfsprogs)`)
        }
        return mountPoint
    }

    private hasXfsTools(): boolean {
        return ['xfs_quota', 'xfs_io'].every(tool =>
            spawnSync('sh', ['-c', `command -v ${tool}`], { encoding: 'utf-8' }).status === 0)
    }

    /**
     * The project id assigned to a directory, or 0 when it has none. Read from the inode
     * rather than tracked here, which is what lets a restarted vm-manager pick up where it
     * left off.
     */
    private projectIdOf(path: string): number {
        const result = spawnSync('xfs_io', ['-r', '-c', 'stat', path], { encoding: 'utf-8' })
        if (result.status !== 0) {
            return 0
        }
        const match = /fsxattr\.projid\s*=\s*(\d+)/.exec(result.stdout ?? '')
        return match ? Number(match[1]) : 0
    }

    /**
     * The lowest project id at or above {@link FIRST_WORKLOAD_PROJECT_ID} that the filesystem
     * is not already accounting. Allocation and assignment both run synchronously, so two
     * concurrent apply calls cannot be handed the same id.
     */
    private nextFreeProjectId(mountPoint: string): number {
        const report = this.runQuota(mountPoint, 'report -p -N -b', 'read the quota report')
        const used = new Set(report.split('\n')
            .map(line => /^#(\d+)/.exec(line.trim())?.[1])
            .filter((id): id is string => id !== undefined)
            .map(Number))
        let ret = FIRST_WORKLOAD_PROJECT_ID
        while (used.has(ret)) {
            ret++
        }
        return ret
    }

    private runQuota(mountPoint: string, command: string, description: string): string {
        const result = spawnSync('xfs_quota', ['-x', '-c', command, mountPoint], { encoding: 'utf-8' })
        const output = `${result.stdout ?? ''}${result.stderr ?? ''}`.trim()
        if (result.status !== 0) {
            throw new Error(`Failed to ${description} on ${mountPoint}: ${output || `exit ${result.status}`}`)
        }
        return output
    }

}
