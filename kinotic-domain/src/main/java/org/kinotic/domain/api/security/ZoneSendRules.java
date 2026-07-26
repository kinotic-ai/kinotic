package org.kinotic.domain.api.security;

import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.utils.ZoneUtil;
import org.kinotic.domain.api.utils.DomainUtil;

import java.util.Set;

/**
 * The zones a participant may address with a send, derived from the participant type. Zones come from the CRI
 * itself, so an un-zoned address is only ever sendable by a participant that may send to any zone.
 */
public class ZoneSendRules {

    private final boolean sendAnyZone;
    private final Set<String> sendZones;

    private ZoneSendRules(boolean sendAnyZone, Set<String> sendZones) {
        this.sendAnyZone = sendAnyZone;
        this.sendZones = sendZones;
    }

    /**
     * Derives the send rules for the given participant: a system participant may send to every zone, an
     * application participant to the {@code app-api} data plane and its own
     * {@code app.<organizationId>.<applicationId>} zone, and an organization participant to the {@code os-api}
     * management surface, {@code app-api}, and its own {@code app.<organizationId>} zones.
     * @param participant the authenticated participant
     * @return the participant's send rules
     */
    public static ZoneSendRules from(Participant participant) {
        ZoneSendRules ret;
        if (participant instanceof SystemParticipant) {

            ret = new ZoneSendRules(true, Set.of());

        } else if (participant instanceof OrganizationParticipant organizationParticipant) {

            // the organization id becomes a zone label, so it is validated the same way
            ZoneUtil.validateLabel(organizationParticipant.getOrganizationId());
            ret = new ZoneSendRules(false, Set.of(DomainUtil.OS_API_ZONE,
                                                  DomainUtil.APP_API_ZONE,
                                                  DomainUtil.APP_ZONE_PREFIX + "." + organizationParticipant.getOrganizationId()));


        } else if (participant instanceof ApplicationParticipant applicationParticipant) {

            // appZone validates the ids, so an id that could shift the zone's label structure
            // fails instead of widening access
            ret = new ZoneSendRules(false, Set.of(DomainUtil.APP_API_ZONE,
                                                  appZone(applicationParticipant.getOrganizationId(),
                                                          applicationParticipant.getApplicationId())));

        } else {
            throw new IllegalArgumentException("Unknown participant type " + participant.getClass().getName()
                                                       + ", no zone routing rules exist for it");
        }
        return ret;
    }

    public boolean sendAllowed(CRI cri) {
        boolean ret;
        if (isRoutableScheme(cri.scheme())) {
            ret = sendAnyZone || zoneAllowed(cri.zone(), sendZones);
        } else {
            ret = false;
        }
        return ret;
    }

    // only srv addresses route through the gateway; stream routing is not yet supported
    public static boolean isRoutableScheme(String scheme) {
        return EventConstants.SERVICE_DESTINATION_SCHEME.equals(scheme);
    }

    // A zone is allowed when it is an allowed zone or a sub-zone of one; the dot boundary keeps
    // 'app.acme-org.orders-app-2' from matching 'app.acme-org.orders-app'
    public static boolean zoneAllowed(String zone, Set<String> allowedZones) {
        boolean ret = false;
        if (zone != null) {
            for (String allowed : allowedZones) {
                if (zone.equals(allowed) || zone.startsWith(allowed + ".")) {
                    ret = true;
                    break;
                }
            }
        }
        return ret;
    }

    /**
     * Builds the zone that all of an application's services live in
     *
     * @param organizationId the id of the organization that owns the application
     * @param applicationId the id of the application
     * @return the application zone, app.&lt;organizationId&gt;.&lt;applicationId&gt;
     */
    private static String appZone(String organizationId, String applicationId) {
        // Each id must be a single dot-free label: a dot inside an id would shift the
        // app.<organizationId>.<applicationId> label structure, letting one (org, app) pair
        // produce the same zone as a different pair plus a sub zone
        ZoneUtil.validateLabel(organizationId);
        ZoneUtil.validateLabel(applicationId);
        return DomainUtil.APP_ZONE_PREFIX + "." + organizationId + "." + applicationId;
    }
}
