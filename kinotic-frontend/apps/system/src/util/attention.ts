import { ExecutionStatus, DeploymentStatusType, WorkloadStatus,
         type JobRun, type Organization, type Workload } from '@kinotic-ai/management-api'
import { VmNodeStatusType, type KinoticClusterInfo, type VmNode } from '@kinotic-ai/system-api'
import { DatetimeUtil } from '@kinotic-ai/frontend-common'
import { organizationPath, scopePath, type Scope } from './scope'

/** One thing an operator has to look at, and where it is handled. */
export interface AttentionItem {
    severity: 'danger' | 'warn'
    icon: string
    text: string
    detail: string
    to: string
}

/** How many failed runs and workloads a list names before it stops. */
const MAX_PER_KIND = 5

function relative(epochMillis: number | null): string {
    return epochMillis ? DatetimeUtil.formatRelativeDate(epochMillis).toLowerCase() : ''
}

function ownerOf(run: { organizationId: string | null; applicationId: string | null; projectId?: string | null }, scope: Scope): string {
    const parts: string[] = []
    if (!scope.organizationId && run.organizationId) parts.push(run.organizationId)
    if (!scope.applicationId && run.applicationId) parts.push(run.applicationId)
    if (!scope.projectId && run.projectId) parts.push(run.projectId)
    return parts.length > 0 ? parts.join(' / ') : (run.organizationId ? 'organization' : 'platform')
}

function failedRuns(runs: JobRun[], scope: Scope): AttentionItem[] {
    return runs.filter(run => run.status === ExecutionStatus.FAILED)
               .slice(0, MAX_PER_KIND)
               .map(run => ({
                   severity: 'danger',
                   icon: 'pi-exclamation-circle',
                   text: `${run.description ?? run.name} failed`,
                   detail: [ownerOf(run, scope), run.error, relative(run.started)].filter(Boolean).join(' · '),
                   to: `${scopePath(scope)}/jobs/${encodeURIComponent(run.id ?? '')}`
               }))
}

function failedWorkloads(workloads: Workload[], scope: Scope): AttentionItem[] {
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
                        to: `${scopePath(scope)}/workloads/${encodeURIComponent(workload.id ?? '')}`
                    }))
}

function unfitNodes(nodes: VmNode[]): AttentionItem[] {
    const ret: AttentionItem[] = []
    for (const node of nodes) {
        if (node.status.type === VmNodeStatusType.DRAINING) {
            ret.push({
                severity: 'warn',
                icon: 'pi-server',
                text: `${node.name} is draining`,
                detail: node.status.healthMessage ?? 'The orchestrator places nothing new on it',
                to: `/worker-nodes/${encodeURIComponent(node.id)}`
            })
        } else if (node.status.type === VmNodeStatusType.OFFLINE) {
            ret.push({
                severity: 'warn',
                icon: 'pi-server',
                text: `${node.name} is offline`,
                detail: [`${node.providerType} on ${node.hostname}`, node.lastSeen ? `last heartbeat ${relative(node.lastSeen)}` : null].filter(Boolean).join(' · '),
                to: `/worker-nodes/${encodeURIComponent(node.id)}`
            })
        }
    }
    return ret
}

// A rolling upgrade that stalls leaves a node on another build than the rest
function versionSkew(cluster: KinoticClusterInfo | null): AttentionItem[] {
    const ret: AttentionItem[] = []
    if (cluster) {
        const byVersion = new Map<string, number>()
        for (const node of cluster.nodes) {
            byVersion.set(node.version, (byVersion.get(node.version) ?? 0) + 1)
        }
        const common = [...byVersion.entries()].sort((a, b) => b[1] - a[1])[0]?.[0]
        for (const node of cluster.nodes) {
            if (common !== undefined && node.version !== common) {
                ret.push({
                    severity: 'warn',
                    icon: 'pi-sync',
                    text: `${node.nodeId} runs ${node.version}`,
                    detail: `The other server nodes run ${common}`,
                    to: '/cluster'
                })
            }
        }
    }
    return ret
}

function storageProblem(organization: Organization, scope: Scope): AttentionItem[] {
    const ret: AttentionItem[] = []
    const status = organization.storage?.status
    const runPath = organization.provisioningJobRunId
        ? `${scopePath(scope)}/jobs/${encodeURIComponent(organization.provisioningJobRunId)}`
        : null
    if (status?.type === DeploymentStatusType.FAILED) {
        ret.push({
            severity: 'danger',
            icon: 'pi-cloud',
            text: `Provisioning of ${organization.name} failed`,
            detail: status.message ?? 'Deployments are blocked until the storage exists',
            to: scope.organizationId ? (runPath ?? scopePath(scope)) : organizationPath(organization.id ?? '')
        })
    } else if (scope.organizationId && status?.type === DeploymentStatusType.PROVISIONING) {
        ret.push({
            severity: 'warn',
            icon: 'pi-hourglass',
            text: 'Storage is being provisioned',
            detail: 'The organization cannot deploy until it is ready',
            to: runPath ?? scopePath(scope)
        })
    }
    return ret
}

/** What needs an operator across the platform, failures first. */
export function platformAttention(cluster: KinoticClusterInfo | null, nodes: VmNode[], workloads: Workload[],
                                  runs: JobRun[], organizations: Organization[]): AttentionItem[] {
    const scope: Scope = {}
    return [
        ...failedRuns(runs, scope),
        ...organizations.flatMap(organization => storageProblem(organization, scope)),
        ...failedWorkloads(workloads, scope),
        ...unfitNodes(nodes),
        ...versionSkew(cluster)
    ]
}

/** What needs an operator within one organization, failures first. */
export function organizationAttention(organization: Organization, workloads: Workload[], runs: JobRun[]): AttentionItem[] {
    const scope: Scope = { organizationId: organization.id ?? '' }
    return [
        ...storageProblem(organization, scope),
        ...failedRuns(runs, scope),
        ...failedWorkloads(workloads, scope)
    ]
}
