import type { MicroserviceDeploymentStatusType, ProjectDeploymentStatusType, UiDeploymentStatusType } from '@kinotic-ai/management-api'

/** Tag severity by status type name, the same across a project's, a microservice's and a UI's deployment status. */
const SEVERITY_BY_TYPE: Record<string, string> = {
  RUNNING: 'success',
  DEPLOYED: 'success',
  READY: 'success',
  FAILED: 'danger',
  ORPHANED: 'warn',
}

/**
 * Maps a project, microservice or UI deployment status type to the PrimeVue Tag severity it
 * renders with: success once serving, danger when failed, warn when orphaned, info meanwhile.
 */
export function deploymentStatusSeverity(type: ProjectDeploymentStatusType | MicroserviceDeploymentStatusType | UiDeploymentStatusType): string {
  return SEVERITY_BY_TYPE[type] ?? 'info'
}
