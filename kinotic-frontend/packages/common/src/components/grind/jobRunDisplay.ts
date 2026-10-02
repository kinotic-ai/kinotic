import { markRaw, type Component } from 'vue'
import { Ban, Check, CircleCheck, CircleDot, CircleSlash, CircleX, Clock, LoaderCircle, X } from '@lucide/vue'
import { ExecutionStatus } from '@kinotic-ai/management-api'

/**
 * Maps an ExecutionStatus to the PrimeVue Tag severity it renders with.
 */
export function executionStatusSeverity(status: ExecutionStatus): string {
  let ret: string
  if (status === ExecutionStatus.COMPLETED) {
    ret = 'success'
  } else if (status === ExecutionStatus.RUNNING) {
    ret = 'info'
  } else if (status === ExecutionStatus.FAILED) {
    ret = 'danger'
  } else if (status === ExecutionStatus.CANCELLED) {
    ret = 'warn'
  } else {
    ret = 'secondary'
  }
  return ret
}

/**
 * How a task or run of each status renders: the badge on its pipeline tile, the circled icon of
 * its ledger row, and the tile behind that icon in a run's summary.
 */
export interface TaskStatusStyle {
  /** The icon inside the status badge on the tile's corner. */
  icon: Component
  /** Classes of the status badge on the tile's corner, null for a task with nothing to report. */
  badge: string | null
  /** The circled icon beside the task's row in the ledger. */
  rowIcon: Component
  /** Classes of the ledger row's icon. */
  row: string
  /** Classes of the tinted tile holding the row icon in a run's summary. */
  tile: string
}

export const TASK_STATUS_STYLE: Record<ExecutionStatus, TaskStatusStyle> = {
  [ExecutionStatus.PENDING]: {
    icon: markRaw(Clock),
    badge: null,
    rowIcon: markRaw(CircleDot),
    row: 'text-surface-300 dark:text-surface-600',
    tile: 'bg-surface-100 dark:bg-surface-800'
  },
  [ExecutionStatus.RUNNING]: {
    icon: markRaw(LoaderCircle),
    badge: 'bg-surface-0 text-sky-600 dark:bg-surface-900 dark:text-sky-300',
    rowIcon: markRaw(LoaderCircle),
    row: 'animate-spin text-sky-500',
    tile: 'bg-sky-50 dark:bg-sky-500/15'
  },
  [ExecutionStatus.COMPLETED]: {
    icon: markRaw(Check),
    badge: 'bg-emerald-500 text-white',
    rowIcon: markRaw(CircleCheck),
    row: 'text-emerald-500',
    tile: 'bg-emerald-50 dark:bg-emerald-500/15'
  },
  [ExecutionStatus.FAILED]: {
    icon: markRaw(X),
    badge: 'bg-red-500 text-white',
    rowIcon: markRaw(CircleX),
    row: 'text-red-500',
    tile: 'bg-red-50 dark:bg-red-500/15'
  },
  [ExecutionStatus.CANCELLED]: {
    icon: markRaw(Ban),
    badge: 'bg-amber-500 text-white',
    rowIcon: markRaw(CircleSlash),
    row: 'text-amber-500',
    tile: 'bg-amber-50 dark:bg-amber-500/15'
  }
}
