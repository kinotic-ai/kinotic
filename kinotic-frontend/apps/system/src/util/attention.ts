import { ExecutionStatus, WorkloadStatus, type JobRun, type Organization, type Workload } from '@kinotic-ai/management-api'
import type { KinoticClusterInfo, VmNode } from '@kinotic-ai/system-api'
import { DatetimeUtil, shortSha } from '@kinotic-ai/frontend-common'
import { AttentionKind } from './AttentionKind'
import { NodeHealth, nodeHealth, nodeUnreachable as nodeMark } from './nodes'
import { scopePath, type Scope } from './scope'
import { nodeUnreachable } from './workloads'

/** One thing an operator has to look at, and where it is handled. */
export interface AttentionItem {
    kind: AttentionKind
    /** What it is, in a few words: the project, workload or node it concerns. */
    title: string
    /** A short tag beside the title, such as the commit a deploy run was for. */
    badge: string | null
    /** Whose it is: the organization, application and project it belongs to, when the scope shown is wider. */
    owner: string | null
    /** Why it needs looking at, in one line. */
    reason: string | null
    /** When it happened, relative to now. */
    when: string
    to: string
}

// A full commit sha reads as noise in a one-line title; its first characters identify it
const FULL_SHA = /\b[0-9a-f]{40}\b/
// "java.lang.IllegalStateException: SBOM workload … failed" reads as its message alone
const EXCEPTION_PREFIX = /^(?:[a-z_$][\w$]*\.)+[A-Z][\w$]*(?:Exception|Error): /

/** How many failed runs and workloads a list names before it stops. */
const MAX_PER_KIND = 5

function relative(epochMillis: number | null): string {
    return epochMillis ? DatetimeUtil.formatRelativeDate(epochMillis).toLowerCase() : ''
}

/** The short form of the commit sha a run's description names, or null when it names none. */
function shortShaIn(description: string | null): string | null {
    const sha = description?.match(FULL_SHA)?.[0]
    return sha ? shortSha(sha) : null
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
                   kind: AttentionKind.FAILED_RUN,
                   // the run concerns its project; the commit it deployed rides beside it
                   title: run.projectId ?? (run.description ?? run.name).replace(FULL_SHA, sha => shortSha(sha)),
                   badge: run.projectId ? shortShaIn(run.description) : null,
                   // the title already names the project, so the owner stops at its application
                   owner: ownerOf({ ...run, projectId: null }, scope),
                   reason: run.error?.replace(EXCEPTION_PREFIX, '') ?? null,
                   when: relative(run.started),
                   to: `${scopePath(scope)}/jobs/${encodeURIComponent(run.id ?? '')}`
               }))
}

function failedWorkloads(workloads: Workload[], scope: Scope): AttentionItem[] {
    return workloads.filter(workload => workload.status === WorkloadStatus.FAILED)
                    .slice(0, MAX_PER_KIND)
                    .map(workload => ({
                        kind: AttentionKind.FAILED_WORKLOAD,
                        title: workload.name,
                        badge: null,
                        owner: ownerOf(workload, scope),
                        reason: [
                            workload.exitCode !== null ? `Exited with code ${workload.exitCode}` : null,
                            workload.nodeId ? `on ${workload.nodeId}` : null
                        ].filter(Boolean).join(' ') || null,
                        when: relative(workload.created),
                        to: `${scopePath(scope)}/workloads/${encodeURIComponent(workload.id ?? '')}`
                    }))
}

function unreachableWorkloads(workloads: Workload[], scope: Scope): AttentionItem[] {
    return workloads.map(workload => ({ workload, unreachable: nodeUnreachable(workload) }))
                    .filter(({ unreachable }) => unreachable !== undefined)
                    .slice(0, MAX_PER_KIND)
                    .map(({ workload, unreachable }) => ({
                        kind: AttentionKind.WORKLOAD_ON_SILENT_NODE,
                        title: workload.name,
                        badge: null,
                        owner: ownerOf(workload, scope),
                        reason: unreachable?.message ?? null,
                        when: unreachable ? relative(unreachable.since) : '',
                        to: `${scopePath(scope)}/workloads/${encodeURIComponent(workload.id ?? '')}`
                    }))
}

function unfitNodes(nodes: VmNode[]): AttentionItem[] {
    const ret: AttentionItem[] = []
    for (const node of nodes) {
        const health = nodeHealth(node)
        if (health === NodeHealth.UNREACHABLE) {
            ret.push({
                kind: AttentionKind.UNREACHABLE_NODE,
                title: node.name,
                badge: null,
                owner: null,
                reason: nodeMark(node)?.message ?? 'Missed its heartbeat; nothing new is placed on it',
                when: node.lastSeen ? `last seen ${relative(node.lastSeen)}` : '',
                to: `/worker-nodes/${encodeURIComponent(node.id)}`
            })
        } else if (health === NodeHealth.DRAINING) {
            ret.push({
                kind: AttentionKind.DRAINING_NODE,
                title: node.name,
                badge: null,
                owner: null,
                reason: node.healthMessage ?? 'Nothing new is placed on it',
                when: '',
                to: `/worker-nodes/${encodeURIComponent(node.id)}`
            })
        }
    }
    return ret
}

// A rolling upgrade that stalls leaves a node on another version than the rest
function versionSkew(cluster: KinoticClusterInfo | null): AttentionItem[] {
    const ret: AttentionItem[] = []
    if (cluster) {
        const byVersion = new Map<string | null, number>()
        for (const node of cluster.nodes) {
            byVersion.set(node.version, (byVersion.get(node.version) ?? 0) + 1)
        }
        const common = [...byVersion.entries()].sort((a, b) => b[1] - a[1])[0]?.[0]
        for (const node of cluster.nodes) {
            if (common !== undefined && node.version !== common) {
                ret.push({
                    kind: AttentionKind.VERSION_SKEW,
                    title: `${node.serverName} node ${node.nodeId}`,
                    badge: null,
                    owner: null,
                    reason: `Runs ${node.version ?? 'an unknown version'}; the other server nodes run ${common ?? 'an unknown version'}`,
                    when: '',
                    to: '/cluster'
                })
            }
        }
    }
    return ret
}

/** What needs an operator across the platform, failures first. */
export function platformAttention(cluster: KinoticClusterInfo | null, nodes: VmNode[], workloads: Workload[],
                                  runs: JobRun[]): AttentionItem[] {
    const scope: Scope = {}
    return [
        ...failedRuns(runs, scope),
        ...failedWorkloads(workloads, scope),
        ...unreachableWorkloads(workloads, scope),
        ...unfitNodes(nodes),
        ...versionSkew(cluster)
    ]
}

/** What needs an operator within one organization, failures first. */
export function organizationAttention(organization: Organization, workloads: Workload[], runs: JobRun[]): AttentionItem[] {
    const scope: Scope = { organizationId: organization.id ?? '' }
    return [
        ...failedRuns(runs, scope),
        ...failedWorkloads(workloads, scope),
        ...unreachableWorkloads(workloads, scope)
    ]
}
