import { Kinotic, Pageable } from '@kinotic-ai/core'
import { ExecutionStatus, WatchEventKind, type JobRun, type Project, type WatchEvent } from '@kinotic-ai/management-api'
import { DatetimeUtil, createDebug, scanJobRuns, shortSha, withoutExceptionPrefix } from '@kinotic-ai/frontend-common'
import { NotificationCategory } from './NotificationCategory'
import type { NotificationItem } from './NotificationItem'

const debug = createDebug('notifications')

/** How far back the feed reaches. */
export const FEED_WINDOW_MS = 7 * 24 * 60 * 60_000

// The feed reads every project's deployment history one request each, so it reads a bounded number
const MAX_PROJECTS = 30
const HISTORY_PER_PROJECT = 20
const MAX_RUNS = 200

const FAILED_PHASES = new Set(['FAILED'])
const DONE_PHASES = new Set(['DEPLOYED', 'READY'])

// The server sends instants as ISO strings although the types declare epoch numbers
function epochOf(value: unknown): number {
    return DatetimeUtil.toEpochMillis(value as number | string | null) ?? 0
}

function runItem(run: JobRun): NotificationItem | null {
    let ret: NotificationItem | null = null
    const to = `/jobs/${encodeURIComponent(run.id ?? '')}`
    const started = epochOf(run.started)
    const ended = epochOf(run.finished) || started
    if (run.id && run.status === ExecutionStatus.FAILED) {
        ret = { key: `run:${run.id}:failed`, category: NotificationCategory.RUN_FAILED, title: `${run.name} failed`,
                detail: run.error ? withoutExceptionPrefix(run.error) : run.description, at: ended, to }
    } else if (run.id && run.status === ExecutionStatus.COMPLETED) {
        ret = { key: `run:${run.id}:completed`, category: NotificationCategory.RUN_COMPLETED, title: `${run.name} completed`,
                detail: run.description, at: ended, to }
    } else if (run.id && run.status === ExecutionStatus.RUNNING) {
        ret = { key: `run:${run.id}:started`, category: NotificationCategory.RUN_STARTED, title: `${run.name} started`,
                detail: run.description, at: started, to }
    }
    return ret
}

function phaseOf(event: WatchEvent): string | null {
    const phase = (event.value as { phase?: unknown } | null)?.phase
    return typeof phase === 'string' ? phase : null
}

function commitOf(event: WatchEvent): string | null {
    const commitSha = (event.value as { commitSha?: unknown } | null)?.commitSha
    return typeof commitSha === 'string' && commitSha ? `At commit ${shortSha(commitSha)}` : null
}

function historyItem(project: Project, event: WatchEvent): NotificationItem | null {
    const name = project.name || project.id || ''
    const to = `/application/${encodeURIComponent(project.applicationId)}/project/${encodeURIComponent(project.id ?? '')}/deployment`
    const at = epochOf(event['@timestamp'])
    const base = { key: `watch:${event.type}:${event.id}:${at}:${event.kind}`, detail: event.message, at, to }
    let ret: NotificationItem | null = null
    if (event.kind === WatchEventKind.CONDITION_SET) {
        ret = { ...base, category: NotificationCategory.DEPLOYMENT_WARNING, title: `${name} needs attention` }
    } else if (event.kind === WatchEventKind.DELETION_REQUESTED) {
        ret = { ...base, category: NotificationCategory.DEPLOYMENT_REMOVED, title: `${name}: removal requested` }
    } else if (event.kind === WatchEventKind.OBSERVED_REPORTED && FAILED_PHASES.has(phaseOf(event) ?? '')) {
        // the observed state's own message is the record's toString; the commit is what reads
        ret = { ...base, detail: commitOf(event), category: NotificationCategory.DEPLOYMENT_FAILED, title: `${name} deployment failed` }
    } else if (event.kind === WatchEventKind.OBSERVED_REPORTED && DONE_PHASES.has(phaseOf(event) ?? '')) {
        ret = { ...base, detail: commitOf(event), category: NotificationCategory.DEPLOYMENT_SUCCEEDED, title: `${name} deployed` }
    }
    return ret
}

async function projectsOf(applicationIds: string[]): Promise<Project[]> {
    const pages = await Promise.allSettled(applicationIds.map(id => Kinotic.projects.findAllForApplication(id, Pageable.create(0, MAX_PROJECTS))))
    return pages.flatMap(page => page.status === 'fulfilled' ? page.value.content ?? [] : []).slice(0, MAX_PROJECTS)
}

/**
 * The organization's activity in the last {@link FEED_WINDOW_MS}, newest first: its job runs, and
 * what each of its projects' deployments reported. A source that fails to load is left out.
 */
export async function loadNotifications(organizationId: string, applicationIds: string[]): Promise<NotificationItem[]> {
    const since = Date.now() - FEED_WINDOW_MS
    const [runs, projects] = await Promise.all([
        scanJobRuns({ organizationId, since }, MAX_RUNS).catch(error => { debug('Failed to load job runs: %O', error); return [] as JobRun[] }),
        projectsOf(applicationIds)
    ])
    const histories = await Promise.allSettled(projects.map(async project => {
        const page = await Kinotic.projects.findDeploymentHistory(project.id ?? '', Pageable.create(0, HISTORY_PER_PROJECT))
        return (page.content ?? []).map(event => historyItem(project, event))
    }))
    const items = [
        ...runs.map(runItem),
        ...histories.flatMap(result => result.status === 'fulfilled' ? result.value : [])
    ]
    return items
        .filter((item): item is NotificationItem => item !== null && item.at >= since)
        .sort((a, b) => b.at - a.at)
}
