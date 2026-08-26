import { RunStatus } from '@kinotic-ai/management-api'

/**
 * Maps an RunStatus to the PrimeVue Tag severity it renders with.
 */
export function runStatusSeverity(status: RunStatus): string {
  let ret: string
  if (status === RunStatus.COMPLETED) {
    ret = 'success'
  } else if (status === RunStatus.RUNNING) {
    ret = 'info'
  } else if (status === RunStatus.FAILED) {
    ret = 'danger'
  } else if (status === RunStatus.CANCELLED) {
    ret = 'warn'
  } else {
    ret = 'secondary'
  }
  return ret
}
