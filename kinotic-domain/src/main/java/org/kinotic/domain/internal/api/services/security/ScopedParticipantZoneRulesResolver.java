package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.event.ZonePartitioningService;
import org.kinotic.core.api.event.ZoneRules;
import org.kinotic.core.api.event.ZoneRulesResolver;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The zone rules of Kinotic OS's participants, as {@link DomainUtil#zoneRules} derives them. An organization's
 * machine, which an application's runtime authenticates as, hosts services in the zones of the applications it may
 * register services of; no other organization participant hosts services.
 */
@Component
@RequiredArgsConstructor
public class ScopedParticipantZoneRulesResolver implements ZoneRulesResolver {

    private static final String CAN_REGISTER_SERVICES = AuthzUtil.permissionName(AuthzConstants.APPLICATION_TYPE, AuthzConstants.CAN_REGISTER_SERVICES);

    private final ZonePartitioningService zonePartitioningService;
    private final RelationshipService relationships;

    @Override
    public Future<ZoneRules> resolve(Participant participant) {
        Validate.notNull(participant, "participant must not be null");
        boolean machine = ParticipantConstants.PARTICIPANT_TYPE_MACHINE.equals(participant.getMetadata()
                                                                                       .get(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY));
        Future<List<String>> hostedZones;
        // only a server hosting the organization's application zones asks the engine, so a server that hosts none
        // connects its organization participants without reaching it
        if (participant instanceof OrganizationParticipant organization
                && machine
                && zonePartitioningService.hostsZone(DomainUtil.APP_ZONE_PREFIX + "." + organization.getOrganizationId())) {
            String organizationId = organization.getOrganizationId();
            String prefix = organizationId + ".";
            hostedZones = relationships.listObjects(AuthzStoreService.SYSTEM,
                                                    AuthzGraphUtil.authzUser(participant),
                                                    CAN_REGISTER_SERVICES,
                                                    AuthzConstants.APPLICATION_TYPE,
                                                    Consistency.MINIMIZE_LATENCY)
                                       .map(applications -> applications.stream()
                                                                        .map(AuthzUtil::idOf)
                                                                        // a machine is granted only within its organization
                                                                        .filter(id -> id.startsWith(prefix))
                                                                        .map(id -> DomainUtil.applicationZone(organizationId, id.substring(prefix.length())))
                                                                        .toList());
        } else {
            hostedZones = Future.succeededFuture(List.of());
        }
        return hostedZones.map(zones -> DomainUtil.zoneRules(participant, zones));
    }
}
