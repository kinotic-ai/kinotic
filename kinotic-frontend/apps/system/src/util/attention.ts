import type { JobRun, Organization, Workload } from '@kinotic-ai/management-api'
import { VmNodeStatusType, type KinoticClusterInfo, type VmNode } from '@kinotic-ai/system-api'
import { DatetimeUtil, failedRunAttention, failedWorkloadAttention, type AttentionItem, type ViewScope } from '@kinotic-ai/frontend-common'
import { nodePath } from './nodes'
import { scopePath } from './scope'

function unfitNodes(nodes: VmNode[]): AttentionItem[] {
    const ret: AttentionItem[] = []
    for (const node of nodes) {
        if (node.status.type === VmNodeStatusType.DRAINING) {
            ret.push({
                severity: 'warn',
                icon: 'pi-server',
                text: `${node.name} is draining`,
                detail: node.status.healthMessage ?? 'The orchestrator places nothing new on it',
                to: nodePath(node.id)
            })
        } else if (node.status.type === VmNodeStatusType.UNREACHABLE) {
            ret.push({
                severity: 'warn',
                icon: 'pi-server',
                text: `${node.name} is unreachable`,
                detail: 'A call to its vm-manager could not be delivered; the orchestrator places nothing new on it',
                to: nodePath(node.id)
            })
        } else if (node.status.type === VmNodeStatusType.OFFLINE) {
            ret.push({
                severity: 'warn',
                icon: 'pi-server',
                text: `${node.name} is offline`,
                detail: [`${node.providerType} on ${node.hostname}`, node.lastSeen ? `last heartbeat ${DatetimeUtil.formatRelativeDate(node.lastSeen).toLowerCase()}` : null].filter(Boolean).join(' · '),
                to: nodePath(node.id)
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

function failures(scope: ViewScope, workloads: Workload[], runs: JobRun[]): AttentionItem[] {
    const base = scopePath(scope)
    return [
        ...failedRunAttention(runs, scope, `${base}/jobs`),
        ...failedWorkloadAttention(workloads, scope, `${base}/workloads`)
    ]
}

/** What needs an operator across the platform, failures first. */
export function platformAttention(cluster: KinoticClusterInfo | null, nodes: VmNode[], workloads: Workload[],
                                  runs: JobRun[]): AttentionItem[] {
    return [
        ...failures({}, workloads, runs),
        ...unfitNodes(nodes),
        ...versionSkew(cluster)
    ]
}

/** What needs an operator within one organization, failures first. */
export function organizationAttention(organization: Organization, workloads: Workload[], runs: JobRun[]): AttentionItem[] {
    return failures({ organizationId: organization.id ?? '' }, workloads, runs)
}
