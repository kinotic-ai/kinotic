package org.kinotic.domain.api.security;

import org.kinotic.core.internal.utils.ZoneUtil;

/**
 * The zones the Kinotic platform partitions the event bus address space into:
 * {@code app.&lt;organizationId&gt;.&lt;applicationId&gt;} addresses belong to a single application,
 * {@link #APP_API} contains the platform's data plane for applications, {@link #OS_API} contains
 * the platform services organizations manage the system through, and {@link #SYSTEM} addresses
 * are internal to the platform. The gateway enforces which zones a participant may address on
 * every send and subscribe.
 */
public final class PlatformZones {

    /**
     * The zone for platform services organizations use to manage the system, such as member,
     * application, and entity definition management
     */
    public static final String OS_API = "os_api";

    /**
     * The zone for the platform's application facing data services, such as entity persistence
     * and named query execution
     */
    public static final String APP_API = "app_api";

    /**
     * The zone for services internal to the platform, only reachable by system participants
     */
    public static final String SYSTEM = "system";

    /**
     * The leading label of application zones, which follow the form app.&lt;organizationId&gt;.&lt;applicationId&gt;
     */
    public static final String APP_PREFIX = "app";

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
        ZoneUtil.validateLabel(organizationId);
        ZoneUtil.validateLabel(applicationId);
        return APP_PREFIX + "." + organizationId + "." + applicationId;
    }

}
