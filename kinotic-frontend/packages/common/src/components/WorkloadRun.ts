import { WorkloadStatus, type Workload } from '@kinotic-ai/management-api'
import DatetimeUtil from '../util/DatetimeUtil'

/** The window a workload ran over, in epoch milliseconds, which the log lines it wrote fall in. */
export interface WorkloadRun {
  /** When the run started, or null while it has not started yet. */
  started: number | null
  /** When the run reached a terminal status, or null while it is still running. */
  finished: number | null
}

/** The run a workload's record describes: its creation, and its last status change once it has ended. */
export function workloadRun(workload: Workload | null | undefined): WorkloadRun | undefined {
  let ret: WorkloadRun | undefined
  if (workload) {
    const ended = workload.status === WorkloadStatus.STOPPED || workload.status === WorkloadStatus.FAILED
    ret = {
      started: DatetimeUtil.toEpochMillis(workload.created),
      finished: ended ? DatetimeUtil.toEpochMillis(workload.updated) : null
    }
  }
  return ret
}
