package org.kinotic.core.api.utils;

import org.apache.commons.lang3.Validate;

import java.util.regex.Pattern;

/**
 * Validates the grammar every zone must satisfy. A zone is the optional leading portion of a
 * service CRI's resourceName; its meaning is defined by whoever enforces routing, core only
 * guarantees the shape.
 */
public final class ZoneUtil {

    // DNS-style label rule, so a zone is always a valid URI authority (CRIs are valid URIs by
    // convention) and can never contain characters that would break CRI parsing, dot-boundary
    // prefix matching, or wildcard patterns. A zone is dot separated labels — both patterns are
    // views of the one grammar. Underscores are excluded because they are illegal in a URI host.
    private static final String LABEL = "[a-z0-9]([a-z0-9-]*[a-z0-9])?";
    private static final Pattern LABEL_PATTERN = Pattern.compile("^" + LABEL + "$");
    private static final Pattern ZONE_PATTERN = Pattern.compile("^" + LABEL + "(\\." + LABEL + ")*$");

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

    /**
     * Validates that the given value is a single zone label
     *
     * @param label the label to validate
     * @throws IllegalArgumentException if the label is empty or is not a single label of
     *         lowercase letters, digits, and interior dashes
     */
    public static void validateLabel(String label) {
        Validate.notEmpty(label, "The label must not be empty");
        Validate.isTrue(LABEL_PATTERN.matcher(label).matches(),
                        "Invalid zone label '%s'. Labels must be lowercase letters, digits, and interior dashes",
                        label);
    }

}
