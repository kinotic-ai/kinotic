package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.RelationshipTuple;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The zone rules of Kinotic OS's participants, as {@link DomainUtil#zoneRules} derives them. An organization's
 * machine, which an application's runtime authenticates as, hosts services in the zones of the applications it may
 * register services of, each decided when the machine first subscribes in it; no other organization participant
 * hosts services.
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
        // derived inside the future, so a participant with no rules fails it
        return Future.succeededFuture(participant).map(this::rulesOf);
    }

    private ZoneRules rulesOf(Participant participant) {
        Map<String, String> metadata = participant.getMetadata() != null ? participant.getMetadata() : Map.of();
        boolean machine = ParticipantConstants.PARTICIPANT_TYPE_MACHINE.equals(metadata.get(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY));
        ZoneRules ret;
        // only a server hosting the organization's application zones asks the engine, so a server that hosts none
        // gives its organization participants no zone to subscribe in
        if (participant instanceof OrganizationParticipant organization
                && machine
                && zonePartitioningService.hostsZone(DomainUtil.APP_ZONE_PREFIX + "." + organization.getOrganizationId())) {
            String organizationId = organization.getOrganizationId();
            String user = AuthzGraphUtil.authzUser(participant);
            // what the engine answered for each application, for as long as these rules are used: a connection
            // subscribes once for every service it hosts, and a machine whose grant is revoked has its
            // connections ended
            Map<String, Future<Boolean>> registers = new ConcurrentHashMap<>();
            ret = DomainUtil.zoneRules(participant, List.of(DomainUtil.APP_ZONE_PREFIX + "." + organizationId))
                            .subscribingWhere(zone -> {
                                // app.<organizationId>.<applicationId>, or a zone within it
                                String[] labels = zone.split("\\.");
                                return labels.length < 3
                                        ? Future.succeededFuture(false)
                                        : registers.computeIfAbsent(labels[2], applicationId -> registers(user, organizationId, applicationId));
                            });
        } else {
            ret = DomainUtil.zoneRules(participant, List.of());
        }
        return ret;
    }

    // Read past the engine's caches: a machine whose grant is revoked has its connections ended and reconnects at
    // once, and an answer kept from before the revocation gives it the zone back
    private Future<Boolean> registers(String user, String organizationId, String applicationId) {
        String application = AuthzUtil.object(AuthzConstants.APPLICATION_TYPE, AuthzGraphUtil.authzApplicationId(organizationId, applicationId));
        return relationships.check(AuthzStoreService.SYSTEM, new RelationshipTuple(user, CAN_REGISTER_SERVICES, application), Consistency.HIGHER_CONSISTENCY);
    }
}
