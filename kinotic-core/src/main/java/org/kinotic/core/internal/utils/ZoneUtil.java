package org.kinotic.core.internal.utils;

import org.apache.commons.lang3.Validate;

import java.util.regex.Pattern;

/**
 * Validates the grammar every zone must satisfy. A zone is the optional leading portion of a
 * service CRI's resourceName; its meaning is defined by whoever enforces routing, core only
 * guarantees the shape.
 */
public final class ZoneUtil {

    // DNS label rule per dot separated label, so a zone can never contain characters that would
    // break CRI parsing, dot-boundary prefix matching, or wildcard patterns
    private static final Pattern ZONE_PATTERN
            = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*$");

    private ZoneUtil() {}

    /**
     * Validates that the given zone satisfies the zone grammar
     *
     * @param zone the zone to validate
     * @throws IllegalArgumentException if the zone is empty or contains anything other than
     *         dot separated labels of lowercase letters, digits, and interior dashes
     */
    public static void validateZone(String zone) {
        Validate.notEmpty(zone, "The zone must not be empty");
        Validate.isTrue(ZONE_PATTERN.matcher(zone).matches(),
                        "Invalid zone '%s'. Zones must be dot separated labels of lowercase letters, digits, and interior dashes",
                        zone);
    }

}
