import { ExecutionStatus, WorkloadStatus, type JobRun, type Workload } from '@kinotic-ai/management-api'
import type { ViewScope } from '../../types/ViewScope'
import DatetimeUtil from '../../util/DatetimeUtil'
import type { AttentionItem } from './AttentionItem'

/** How many failed runs and workloads a list names before it stops. */
const MAX_PER_KIND = 5

function relative(epochMillis: number | null): string {
    return epochMillis ? DatetimeUtil.formatRelativeDate(epochMillis).toLowerCase() : ''
}

// Names what the scope leaves unsaid, e.g. the application and project inside an organization
function ownerOf(owned: { organizationId: string | null; applicationId: string | null; projectId?: string | null },
                 scope: ViewScope): string {
    const parts: string[] = []
    if (!scope.organizationId && owned.organizationId) parts.push(owned.organizationId)
    if (!scope.applicationId && owned.applicationId) parts.push(owned.applicationId)
    if (!scope.projectId && owned.projectId) parts.push(owned.projectId)
    return parts.length > 0 ? parts.join(' / ') : (owned.organizationId ? 'organization' : 'platform')
}

/**
 * The failed runs among the given ones, newest first as given, each leading to its page under
 * {@code jobsPath}, the Jobs list of the scope.
 */
export function failedRunAttention(runs: JobRun[], scope: ViewScope, jobsPath: string): AttentionItem[] {
    return runs.filter(run => run.status === ExecutionStatus.FAILED)
               .slice(0, MAX_PER_KIND)
               .map(run => ({
                   severity: 'danger',
                   icon: 'pi-exclamation-circle',
                   text: `${run.description ?? run.name} failed`,
                   detail: [ownerOf(run, scope), run.error, relative(run.started)].filter(Boolean).join(' · '),
                   to: `${jobsPath}/${encodeURIComponent(run.id ?? '')}`
               }))
}

/**
 * The failed workloads among the given ones, each leading to its page under
 * {@code workloadsPath}, the Workloads list of the scope.
 */
export function failedWorkloadAttention(workloads: Workload[], scope: ViewScope, workloadsPath: string): AttentionItem[] {
    return workloads.filter(workload => workload.status === WorkloadStatus.FAILED)
                    .slice(0, MAX_PER_KIND)
                    .map(workload => ({
                        severity: 'danger',
                        icon: 'pi-box',
                        text: `Workload ${workload.name} failed`,
                        detail: [
                            ownerOf(workload, scope),
                            workload.exitCode !== null ? `exit code ${workload.exitCode}` : null,
                            workload.nodeId ? `on ${workload.nodeId}` : null,
                            relative(workload.created)
                        ].filter(Boolean).join(' · '),
                        to: `${workloadsPath}/${encodeURIComponent(workload.id ?? '')}`
                    }))
}
