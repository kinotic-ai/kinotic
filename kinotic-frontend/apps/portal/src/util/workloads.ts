import { Kinotic } from '@kinotic-ai/core'
import type { Workload } from '@kinotic-ai/management-api'
import { scanWorkloadPages, type ViewScope } from '@kinotic-ai/frontend-common'

/**
 * Every workload of the organization in the scope, newest first: the application narrows the
 * read server-side, the project is matched by name on what comes back.
 */
export function scanWorkloads(scope: ViewScope): Promise<Workload[]> {
    const applicationId = scope.applicationId
    return scanWorkloadPages(pageable => applicationId
                                 ? Kinotic.workloadMonitoring.findWorkloadsForApplication(applicationId, pageable)
                                 : Kinotic.workloadMonitoring.findWorkloads(pageable),
                             scope.projectId)
}
