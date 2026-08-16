import { ExecutionStatus } from '@kinotic-ai/os-api'

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
 * Formats the elapsed time of a run or step, measuring against nowMs while it has not
 * finished. Returns an em dash when it never started.
 */
export function formatDuration(started: number | null, finished: number | null, nowMs: number = Date.now()): string {
  let ret: string
  if (!started) {
    ret = '—'
  } else {
    const totalSeconds = Math.max(0, Math.floor(((finished ?? nowMs) - started) / 1000))
    const hours = Math.floor(totalSeconds / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const seconds = totalSeconds % 60
    if (hours > 0) {
      ret = `${hours}h ${minutes}m`
    } else if (minutes > 0) {
      ret = `${minutes}m ${seconds}s`
    } else {
      ret = `${seconds}s`
    }
  }
  return ret
}
