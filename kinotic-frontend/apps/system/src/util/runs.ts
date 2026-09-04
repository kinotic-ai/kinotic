import type { JobRun } from '@kinotic-ai/management-api'

// ProjectDeployJobDefinitionFactory describes a deploy run as "Deploy project <id> at <sha>"
const COMMIT_SUFFIX = / at ([0-9a-f]{7,40})$/i

/** Whether the run deployed a project; a run with no project provisioned an organization. */
export function isDeployRun(run: JobRun): boolean {
    return run.projectId !== null
}

/** The commit a deploy run brought the project to, or null for another kind of run. */
export function commitShaOf(run: JobRun): string | null {
    return run.description?.match(COMMIT_SUFFIX)?.[1] ?? null
}

/** The deploy runs of each project, newest first, keyed by project id. */
export function deployRunsByProject(runs: JobRun[]): Map<string, JobRun[]> {
    const ret = new Map<string, JobRun[]>()
    for (const run of runs) {
        if (run.projectId !== null) {
            const list = ret.get(run.projectId) ?? []
            list.push(run)
            ret.set(run.projectId, list)
        }
    }
    return ret
}
