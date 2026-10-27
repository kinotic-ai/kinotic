import { Direction, Kinotic, Order, Pageable, Sort } from '@kinotic-ai/core'
import { JobRunFilter, type JobRun } from '@kinotic-ai/management-api'

/** How many runs a scan returns at most. */
export const JOB_RUN_SCAN_LIMIT = 500

const SCAN_PAGE_SIZE = 100

/** The filter that narrows the runs by the given fields, each field left out matching every run. */
export function jobRunFilter(fields: Partial<JobRunFilter>): JobRunFilter {
  return Object.assign(new JobRunFilter(), fields)
}

/** How many job runs the caller may view match the filter. */
export async function countJobRuns(fields: Partial<JobRunFilter>): Promise<number> {
  const page = await Kinotic.jobMonitoring.findJobRuns(jobRunFilter(fields), Pageable.create(0, 1, null))
  return page.totalElements ?? 0
}

/**
 * The job runs the caller may view that match the filter, most recently started first, at most
 * {@code limit} of them.
 */
export async function scanJobRuns(fields: Partial<JobRunFilter>, limit: number = JOB_RUN_SCAN_LIMIT): Promise<JobRun[]> {
  const filter = jobRunFilter(fields)
  const sort = new Sort()
  sort.orders = [new Order('started', Direction.DESC)]
  const ret: JobRun[] = []
  for (let pageNumber = 0; ret.length < limit; pageNumber++) {
    const page = await Kinotic.jobMonitoring.findJobRuns(filter, Pageable.create(pageNumber, SCAN_PAGE_SIZE, sort))
    const content = page.content ?? []
    ret.push(...content.slice(0, limit - ret.length))
    if (content.length < SCAN_PAGE_SIZE) {
      break
    }
  }
  return ret
}
