import type { DeploymentStatusType } from '@kinotic-ai/management-api'

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
