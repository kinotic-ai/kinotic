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
