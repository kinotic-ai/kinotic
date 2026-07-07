/**
 * The well known zone names and the grammar every zone must satisfy.
 *
 * Zones partition the event bus address space: `app.<organizationId>.<applicationId>` addresses
 * belong to a single application, `system` addresses are internal to the platform, and every
 * other zone (such as `api`) contains platform services registered in process.
 */

/**
 * The conventional zone for platform services that applications and organizations may call
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

// DNS label rule per dot separated label, so a zone can never contain characters that would
// break CRI parsing, dot-boundary prefix matching, or wildcard patterns
const LABEL_PATTERN = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/
const ZONE_PATTERN = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*$/

/**
 * Validates that the given zone satisfies the zone grammar
 * @param zone the zone to validate
 * @throws if the zone is empty or contains anything other than dot separated labels of
 *         lowercase letters, digits, and interior dashes
 */
export function validateZone(zone: string): void {
    if (!zone || !ZONE_PATTERN.test(zone)) {
        throw new Error(`Invalid zone '${zone}'. Zones must be dot separated labels of lowercase letters, digits, and interior dashes`)
    }
}

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

/**
 * Validates that the given value is a single zone label
 * @param label the label to validate
 * @throws if the label is empty or is not a single label of lowercase letters, digits, and
 *         interior dashes
 */
export function validateLabel(label: string): void {
    if (!label || !LABEL_PATTERN.test(label)) {
        throw new Error(`Invalid zone label '${label}'. Labels must be lowercase letters, digits, and interior dashes`)
    }
}
