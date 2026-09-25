import type { DeploymentState, DeploymentStatusType } from '@kinotic-ai/management-api'

/** Tag severity by status type name. */
const SEVERITY_BY_TYPE: Record<string, string> = {
  RUNNING: 'success',
  DEPLOYED: 'success',
  READY: 'success',
  FAILED: 'danger',
  ORPHANED: 'warn',
}

/**
 * Maps a deployment status type to the PrimeVue Tag severity it renders with: success once
 * serving, danger when failed, warn when orphaned, info meanwhile.
 */
export function deploymentStatusSeverity(type: DeploymentStatusType): string {
  return SEVERITY_BY_TYPE[type] ?? 'info'
}

/** The phase a deployment reports, or PENDING while its worker has yet to answer. */
export function observedPhase(observed: DeploymentState | null | undefined): string {
  return observed?.phase ?? 'PENDING'
}

/** The Tag severity of the phase a deployment reports, secondary while its worker has yet to answer. */
export function observedPhaseSeverity(observed: DeploymentState | null | undefined): string {
  return observed ? deploymentStatusSeverity(observed.phase) : 'secondary'
}
