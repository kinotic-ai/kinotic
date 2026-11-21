package org.kinotic.testserver;

import io.vertx.core.Future;
import org.kinotic.core.api.event.ZoneRules;
import org.kinotic.core.api.event.ZoneRulesResolver;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link ZoneRulesResolver} of the test server, which runs without the domain module's services and so without
 * the authorization engine: an organization participant hosts services in every application zone of its
 * organization, so the {@code @kinotic-ai/core} suite can publish a service in any application's zone.
 */
@Component
public class TestZoneRulesResolver implements ZoneRulesResolver {

    @Override
    public Future<ZoneRules> resolve(Participant participant) {
        List<String> hostedZones = participant instanceof OrganizationParticipant organization
                ? List.of(DomainUtil.APP_ZONE_PREFIX + "." + organization.getOrganizationId())
                : List.of();
        return Future.succeededFuture(hostedZones).map(zones -> DomainUtil.zoneRules(participant, zones));
    }
}
