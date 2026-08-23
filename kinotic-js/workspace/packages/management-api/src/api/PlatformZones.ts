import { validateLabel } from '@kinotic-ai/core'

/**
 * The zones the Kinotic platform partitions the event bus address space into:
 * `app.<organizationId>.<applicationId>` addresses belong to a single application, `app-api`
 * contains the platform's data plane for applications, `management-api` contains the platform services
 * organizations manage the system through, and `system-api` addresses are internal to the platform.
 * The gateway enforces which zones a participant may address on every send and subscribe.
 */

// The data plane zone lives in @kinotic-ai/persistence, whose services occupy it, and is
// re-exported here so consumers of the platform zones can reach every zone from one import
export { APP_API_ZONE } from '@kinotic-ai/persistence'

/**
 * The zone for platform services organizations use to manage the system, such as member,
 * application, and entity definition management
 */
export const MANAGEMENT_API_ZONE = 'management-api'

// The system zone lives in @kinotic-ai/system-api, whose services occupy it, and is
// re-exported here for the same one-import reason
export { SYSTEM_API_ZONE } from '@kinotic-ai/system-api'

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
