package org.kinotic.domain.internal.api.services.security;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.event.ZoneRules;
import org.kinotic.core.api.event.ZoneRulesResolver;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.utils.ZoneUtil;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.security.participant.ApplicationParticipant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.domain.api.model.security.participant.SystemParticipant;
import org.kinotic.domain.api.utils.DomainUtil;

import java.util.Set;

/**
 * The zone rules of Kinotic OS's participants, decided by the participant type. Application participants send to
 * the {@code app-api} data plane and their own {@code app.<organizationId>.<applicationId>} zone and subscribe in no
 * zone at all. Organization participants send to the {@code management-api} management surface, {@code app-api},
 * and their own {@code app.<organizationId>} zones, and subscribe within those same app zones — an application's
 * runtime authenticates as an organization participant to host and call its services. System participants send
 * everywhere and subscribe in the {@code system-api} zone.
 */
public class ScopedParticipantZoneRulesResolver implements ZoneRulesResolver {

    @Override
    public ZoneRules resolve(Participant participant) {
        Validate.notNull(participant, "participant must not be null");
        if (!(participant instanceof ScopedParticipant scopedParticipant)) {
            throw new IllegalArgumentException("Unknown participant type " + participant.getClass().getName()
                                                       + ", no zone routing rules exist for it");
        }
        return switch (scopedParticipant) {

            // management-api and app-api are hosted in-process only, so no connection may ever
            // subscribe to them; system-api stays subscribable for the vm-manager nodes that host there
            case SystemParticipant _ -> ZoneRules.sendingToAnyZone(Set.of(DomainUtil.SYSTEM_API_ZONE));

            // appZone validates the ids, so an id that could shift the zone's label structure
            // fails instead of widening access
            case ApplicationParticipant applicationParticipant ->
                    ZoneRules.of(Set.of(DomainUtil.APP_API_ZONE,
                                        appZone(new ApplicationKey(applicationParticipant.getOrganizationId(),
                                                                   applicationParticipant.getApplicationId()))),
                                 Set.of());

            case OrganizationParticipant organizationParticipant -> {
                // the organization id becomes a zone label, so it is validated the same way
                ZoneUtil.validateLabel(organizationParticipant.getOrganizationId());
                String orgAppsZone = DomainUtil.APP_ZONE_PREFIX + "." + organizationParticipant.getOrganizationId();
                yield ZoneRules.of(Set.of(DomainUtil.MANAGEMENT_API_ZONE, DomainUtil.APP_API_ZONE, orgAppsZone),
                                   Set.of(orgAppsZone));
            }
        };
    }

    /**
     * Builds the zone that all of an application's services live in
     *
     * @param applicationKey the application
     * @return the application zone, app.&lt;organizationId&gt;.&lt;applicationId&gt;
     */
    private static String appZone(ApplicationKey applicationKey) {
        // Each id must be a single dot-free label: a dot inside an id would shift the
        // app.<organizationId>.<applicationId> label structure, letting one (org, app) pair
        // produce the same zone as a different pair plus a sub zone
        ZoneUtil.validateLabel(applicationKey.organizationId());
        ZoneUtil.validateLabel(applicationKey.applicationId());
        return DomainUtil.applicationZone(applicationKey.organizationId(), applicationKey.applicationId());
    }
}
