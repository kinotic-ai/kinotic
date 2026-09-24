import { Direction, Order, Pageable, Sort, type Page } from '@kinotic-ai/core'
import type { Workload } from '@kinotic-ai/management-api'

/**
 * How many workloads a scan reads. A scope with more is undercounted until a server-side
 * aggregation exists.
 */
export const WORKLOAD_SCAN_LIMIT = 1000

const SCAN_PAGE_SIZE = 100

/**
 * Whether the deployment job started the workload for the project. ProjectDeployJobDefinitionFactory
 * names the sync, publish and runtime workloads it starts after the project, which is the only
 * record of the project a workload carries.
 */
export function belongsToProject(workload: Workload, projectId: string): boolean {
    return workload.name === `project-sync-${projectId}`
        || workload.name === `project-ui-publish-${projectId}`
        || workload.name.startsWith(`project-runtime-${projectId}-`)
}

/**
 * Every workload the finder pages through, newest first, up to {@link WORKLOAD_SCAN_LIMIT};
 * given a project, only the ones its deployments started.
 */
export async function scanWorkloadPages(findPage: (pageable: Pageable) => Promise<Page<Workload>>,
                                        projectId?: string): Promise<Workload[]> {
    const sort = new Sort()
    sort.orders = [new Order('created', Direction.DESC)]
    const ret: Workload[] = []
    for (let pageNumber = 0; ret.length < WORKLOAD_SCAN_LIMIT; pageNumber++) {
        const page = await findPage(Pageable.create(pageNumber, SCAN_PAGE_SIZE, sort))
        const content = page.content ?? []
        ret.push(...content)
        if (content.length < SCAN_PAGE_SIZE) {
            break
        }
    }
    return projectId ? ret.filter(workload => belongsToProject(workload, projectId)) : ret
}
