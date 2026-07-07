package org.kinotic.core.api.service;

import org.apache.commons.lang3.Validate;

import java.util.regex.Pattern;

/**
 * The well known zone names and the grammar every zone must satisfy.
 *
 * Zones partition the event bus address space: {@code app.&lt;organizationId&gt;.&lt;applicationId&gt;}
 * addresses belong to a single application, {@code system} addresses are internal to the platform,
 * and every other zone (such as {@link #API}) contains platform services registered in process.
 */
public final class ServiceZones {

    /**
     * The conventional zone for platform services that applications and organizations may call
     */
    public static final String API = "api";

    /**
     * The zone for services internal to the platform, only reachable by system participants
     */
    public static final String SYSTEM = "system";

    /**
     * The leading label of application zones, which follow the form app.&lt;organizationId&gt;.&lt;applicationId&gt;
     */
    public static final String APP_PREFIX = "app";

    // DNS label rule per dot separated label, so a zone can never contain characters that would
    // break CRI parsing, dot-boundary prefix matching, or wildcard patterns
    private static final Pattern LABEL_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");
    private static final Pattern ZONE_PATTERN
            = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*$");

    private ServiceZones() {}

    /**
     * Builds the zone that all of an application's services live in
     *
     * @param organizationId the id of the organization that owns the application
     * @param applicationId the id of the application
     * @return the application zone, app.&lt;organizationId&gt;.&lt;applicationId&gt;
     */
    public static String appZone(String organizationId, String applicationId) {
        // Each id must be a single dot-free label: a dot inside an id would shift the
        // app.<organizationId>.<applicationId> label structure, letting one (org, app) pair
        // produce the same zone as a different pair plus a sub zone
        validateLabel(organizationId);
        validateLabel(applicationId);
        return APP_PREFIX + "." + organizationId + "." + applicationId;
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
