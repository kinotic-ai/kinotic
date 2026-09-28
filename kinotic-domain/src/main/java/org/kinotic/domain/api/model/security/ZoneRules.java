package org.kinotic.domain.api.model.security;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.ZonePartition;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.utils.ZoneUtil;
import org.kinotic.domain.api.model.security.participant.ApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.domain.api.model.security.participant.SystemParticipant;
import org.kinotic.domain.api.utils.DomainUtil;

import java.util.Set;

/**
 * The zones a participant may address, derived once from the participant type: the zones it may send to and the
 * zones it may subscribe in. Zones come from the CRI itself, so an un-zoned address is only ever sendable by a
 * participant that may send to any zone. {@link #restrictedTo(ZonePartition)} narrows the rules to what one server
 * serves.
 */
public class ZoneRules {

    private static final ZonePartition EVERY_ZONE = ZonePartition.everyZone("every-zone");

    private final boolean sendAnyZone;
    private final Set<String> sendZones;
    private final Set<String> subscribableZones;
    private final ZonePartition partition;

    private ZoneRules(boolean sendAnyZone, Set<String> sendZones, Set<String> subscribableZones, ZonePartition partition) {
        this.sendAnyZone = sendAnyZone;
        this.sendZones = sendZones;
        this.subscribableZones = subscribableZones;
        this.partition = partition;
    }

    /**
     * Derives the zone rules for the given participant. Application participants send to the {@code app-api}
     * data plane and their own {@code app.<organizationId>.<applicationId>} zone and subscribe in no zone at
     * all. Organization participants send to the {@code management-api} management surface, {@code app-api},
     * and their own {@code app.<organizationId>} zones, and subscribe within those same app zones — an
     * application's runtime authenticates as an organization participant to host and call its services.
     * System participants send everywhere and subscribe in the {@code system-api} zone.
     * @param participant the authenticated participant
     * @return the participant's zone rules
     */
    public static ZoneRules from(Participant participant) {
        Validate.notNull(participant, "participant must not be null");
        if (!(participant instanceof ScopedParticipant scopedParticipant)) {
            throw new IllegalArgumentException("Unknown participant type " + participant.getClass().getName()
                                                       + ", no zone routing rules exist for it");
        }
        return switch (scopedParticipant) {

            // management-api and app-api are hosted in-process only, so no connection may ever
            // subscribe to them; system-api stays subscribable for the vm-manager nodes that host there
            case SystemParticipant _ -> new ZoneRules(true, Set.of(), Set.of(DomainUtil.SYSTEM_API_ZONE), EVERY_ZONE);

            // appZone validates the ids, so an id that could shift the zone's label structure
            // fails instead of widening access
            case ApplicationParticipant applicationParticipant ->
                    new ZoneRules(false,
                                  Set.of(DomainUtil.APP_API_ZONE,
                                         appZone(applicationParticipant.getOrganizationId(),
                                                 applicationParticipant.getApplicationId())),
                                  Set.of(),
                                  EVERY_ZONE);

            case OrganizationParticipant organizationParticipant -> {
                // the organization id becomes a zone label, so it is validated the same way
                ZoneUtil.validateLabel(organizationParticipant.getOrganizationId());
                String orgAppsZone = DomainUtil.APP_ZONE_PREFIX + "." + organizationParticipant.getOrganizationId();
                yield new ZoneRules(false,
                                    Set.of(DomainUtil.MANAGEMENT_API_ZONE, DomainUtil.APP_API_ZONE, orgAppsZone),
                                    Set.of(orgAppsZone),
                                    EVERY_ZONE);
            }
        };
    }

    /**
     * These rules narrowed to what one server serves: a send only to a zone the partition reaches, a subscription
     * only in a zone it hosts.
     *
     * @param partition the server's zone partition
     * @return the narrowed rules
     */
    public ZoneRules restrictedTo(ZonePartition partition) {
        Validate.notNull(partition, "partition must not be null");
        return new ZoneRules(sendAnyZone, sendZones, subscribableZones, partition);
    }

    public boolean sendAllowed(CRI cri) {
        boolean ret;
        if (isRoutableScheme(cri.scheme())) {
            ret = (sendAnyZone || zoneAllowed(cri.zone(), sendZones)) && partition.reaches(cri.baseResource());
        } else {
            ret = false;
        }
        return ret;
    }

    public boolean subscribeAllowed(CRI cri) {
        boolean ret;
        if (isRoutableScheme(cri.scheme())) {
            ret = zoneAllowed(cri.zone(), subscribableZones) && partition.hosts(cri.baseResource());
        } else {
            ret = false;
        }
        return ret;
    }

    // only srv addresses route through the gateway; stream routing is not yet supported
    private static boolean isRoutableScheme(String scheme) {
        return EventConstants.SERVICE_DESTINATION_SCHEME.equals(scheme);
    }

    private static boolean zoneAllowed(String zone, Set<String> allowedZones) {
        return zone != null && ZoneUtil.zoneMatches(zone, allowedZones);
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
