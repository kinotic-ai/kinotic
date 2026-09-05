import { Direction, Kinotic, Order, Pageable, Sort } from '@kinotic-ai/core'
import type { ExecutionStatus, JobRun } from '@kinotic-ai/management-api'
import DatetimeUtil from '../../util/DatetimeUtil'

/**
 * Which job runs a scan keeps. An unset field matches every run; {@code organizationId} set
 * to null keeps the runs that belong to no organization, the platform's own.
 */
export interface JobRunFilter {
  organizationId?: string | null
  applicationId?: string
  projectId?: string
  status?: ExecutionStatus
  /** Keeps the runs started at or after this epoch millisecond. */
  since?: number
}

/** How many runs a scan reads before it stops looking for more matches. */
export const JOB_RUN_SCAN_LIMIT = 500

const SCAN_PAGE_SIZE = 100

/** Whether the run is one the filter keeps. */
export function matchesJobRunFilter(run: JobRun, filter: JobRunFilter): boolean {
  return (filter.organizationId === undefined || run.organizationId === filter.organizationId)
      && (filter.applicationId === undefined || run.applicationId === filter.applicationId)
      && (filter.projectId === undefined || run.projectId === filter.projectId)
      && (filter.status === undefined || run.status === filter.status)
      && (filter.since === undefined || (DatetimeUtil.toEpochMillis(run.started) ?? 0) >= filter.since)
}

/**
 * The job runs the caller may view that match the filter, most recently started first. The
 * scan reads runs newest first and stops at the first one started before {@code since}, or
 * once {@code limit} runs have been read, so an older match past that point is not found.
 */
export async function scanJobRuns(filter: JobRunFilter, limit: number = JOB_RUN_SCAN_LIMIT): Promise<JobRun[]> {
  const sort = new Sort()
  sort.orders = [new Order('started', Direction.DESC)]
  const ret: JobRun[] = []
  let read = 0
  for (let pageNumber = 0; read < limit; pageNumber++) {
    const page = await Kinotic.jobMonitoring.findJobRuns(Pageable.create(pageNumber, SCAN_PAGE_SIZE, sort))
    const content = page.content ?? []
    for (const run of content) {
      if (matchesJobRunFilter(run, filter)) {
        ret.push(run)
      }
    }
    read += content.length
    const oldest = content[content.length - 1]
    const pastSince = filter.since !== undefined && oldest !== undefined
        && (DatetimeUtil.toEpochMillis(oldest.started) ?? 0) < filter.since
    if (content.length < SCAN_PAGE_SIZE || pastSince) {
      break
    }
  }
  return ret
}
