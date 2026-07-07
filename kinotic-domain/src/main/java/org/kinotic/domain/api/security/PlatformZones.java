package org.kinotic.domain.api.security;

import org.apache.commons.lang3.Validate;

import java.util.regex.Pattern;

/**
 * The zones the Kinotic platform partitions the event bus address space into:
 * {@code app.&lt;organizationId&gt;.&lt;applicationId&gt;} addresses belong to a single application,
 * {@link #SYSTEM} addresses are internal to the platform, and every other zone (such as
 * {@link #API}) contains platform services registered in process. The gateway enforces which
 * zones a participant may address on every send and subscribe.
 */
public final class PlatformZones {

    /**
     * The zone for platform services that applications and organizations may call
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

    // Single dot-free label of the zone grammar
    private static final Pattern LABEL_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");

    private PlatformZones() {}

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

    private static void validateLabel(String label) {
        Validate.notEmpty(label, "The label must not be empty");
        Validate.isTrue(LABEL_PATTERN.matcher(label).matches(),
                        "Invalid zone label '%s'. Labels must be lowercase letters, digits, and interior dashes",
                        label);
    }

}
