/**
 * The zones the Kinotic platform partitions the event bus address space into:
 * `app.<organizationId>.<applicationId>` addresses belong to a single application, `app_api`
 * contains the platform's data plane for applications, `os_api` contains the platform services
 * organizations manage the system through, and `system` addresses are internal to the platform.
 * The gateway enforces which zones a participant may address on every send and subscribe.
 */

/**
 * The zone for platform services organizations use to manage the system, such as member,
 * application, and entity definition management
 */
export const OS_API_ZONE = 'os_api'

/**
 * The zone for the platform's application facing data services, such as entity persistence
 * and named query execution
 */
export const APP_API_ZONE = 'app_api'

/**
 * The zone for services internal to the platform, only reachable by system participants
 */
export const SYSTEM_ZONE = 'system'

/**
 * The leading label of application zones, which follow the form app.<organizationId>.<applicationId>
 */
export const APP_ZONE_PREFIX = 'app'

// Single dot-free label of the zone grammar
const LABEL_PATTERN = /^[a-z0-9]([a-z0-9_-]*[a-z0-9])?$/

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

function validateLabel(label: string): void {
    if (!label || !LABEL_PATTERN.test(label)) {
        throw new Error(`Invalid zone label '${label}'. Labels must be lowercase letters, digits, and interior dashes or underscores`)
    }
}
