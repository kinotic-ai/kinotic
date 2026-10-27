import { validateLabel } from '@kinotic-ai/core'

/**
 * The zones Kinotic partitions the event bus address space into:
 * `app.<organizationId>.<applicationId>` addresses belong to a single application, `app-api`
 * contains the data plane for applications, `management-api` contains the services organizations
 * manage their applications through, and `system-api` contains the system's own services.
 * The gateway enforces which zones a participant may address on every send and subscribe.
 */

// The data plane zone lives in @kinotic-ai/persistence, whose services occupy it, and is
// re-exported here so consumers can reach every zone from one import
export { APP_API_ZONE } from '@kinotic-ai/persistence'

/**
 * The zone for the services organizations use to manage their members, applications and
 * entity definitions
 */
export const MANAGEMENT_API_ZONE = 'management-api'

/**
 * The zone for the system's own services, only reachable by system participants
 */
export const SYSTEM_API_ZONE = 'system-api'

/**
 * The leading label of application zones, which follow the form app.<organizationId>.<applicationId>
 */
export const APP_ZONE_PREFIX = 'app'

/**
 * Builds the zone that all of an application's services live in
 * @param organizationId the id of the organization that owns the application
 * @param applicationId the id of the application
 * @return the application zone, app.<organizationId>.<applicationId>
 */
export function appZone(organizationId: string, applicationId: string): string {
    // Each id must be a single dot-free label: a dot inside an id would shift the
    // app.<organizationId>.<applicationId> label structure, letting one (org, app) pair
    // produce the same zone as a different pair plus a sub zone
    validateLabel(organizationId)
    validateLabel(applicationId)
    return `${APP_ZONE_PREFIX}.${organizationId}.${applicationId}`
}
