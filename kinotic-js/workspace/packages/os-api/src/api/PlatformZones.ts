import { validateLabel } from '@kinotic-ai/core'

/**
 * The zones the Kinotic platform partitions the event bus address space into:
 * `app.<organizationId>.<applicationId>` addresses belong to a single application, `system`
 * addresses are internal to the platform, and every other zone (such as `api`) contains
 * platform services registered in process. The gateway enforces which zones a participant may
 * address on every send and subscribe.
 */

/**
 * The zone for platform services that applications and organizations may call
 */
export const API_ZONE = 'api'

/**
 * The zone for services internal to the platform, only reachable by system participants
 */
export const SYSTEM_ZONE = 'system'

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
