/**
 * Validates the grammar every zone must satisfy. A zone is the optional leading portion of a
 * service CRI's resourceName; its meaning is defined by whoever enforces routing, core only
 * guarantees the shape.
 */

// DNS-style label rule, so a zone is always a valid URI authority (CRIs are valid URIs by
// convention) and can never contain characters that would break CRI parsing, dot-boundary
// prefix matching, or wildcard patterns. A zone is dot separated labels — both patterns are
// views of the one grammar. Underscores are excluded because they are illegal in a URI host.
const LABEL = '[a-z0-9]([a-z0-9-]*[a-z0-9])?'
const LABEL_PATTERN = new RegExp(`^${LABEL}$`)
const ZONE_PATTERN = new RegExp(`^${LABEL}(\\.${LABEL})*$`)

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
