package org.kinotic.gateway.internal.endpoints.stomp;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.api.security.ApplicationParticipant;
import org.kinotic.domain.api.security.OrganizationParticipant;
import org.kinotic.domain.api.security.SystemParticipant;
import org.kinotic.domain.api.security.ZoneSendRules;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Creates STOMP authorizers using the gateway's current participant routing rules.
 *
 * A participant may only address zones its type allows. Application participants send to the
 * {@code app-api} data plane and their own {@code app.<organizationId>.<applicationId>} zone and
 * subscribe only to reply destinations. Organization participants send to the {@code os-api}
 * management surface, {@code app-api}, and their own {@code app.<organizationId>} zones, and
 * subscribe within those same app zones — an application's runtime authenticates as an
 * organization participant to host and call its services. System participants send everywhere
 * and subscribe in the {@code system} zone.
 */
@Component
public class StompAuthorizerFactory {

    public StompAuthorizer create(ConnectedInfo connectedInfo) {
        Validate.notNull(connectedInfo, "connectedInfo must not be null");
        Validate.notNull(connectedInfo.getParticipant(), "participant must not be null");
        Validate.notEmpty(connectedInfo.getReplyToId(), "replyToId must not be empty");

        Participant participant = connectedInfo.getParticipant();
        ZoneSendRules sendRules = ZoneSendRules.from(participant);

        Set<String> subscribableZones;
        if (participant instanceof SystemParticipant) {
            // os-api and app-api are hosted in-process only, so no connection may ever subscribe
            // to them; the system zone stays subscribable for the vm-manager nodes that host there
            subscribableZones = Set.of(DomainUtil.SYSTEM_ZONE);
        } else if (participant instanceof ApplicationParticipant) {
            subscribableZones = Set.of();
        } else {
            // ZoneSendRules.from rejected every other type, so this is an OrganizationParticipant
            subscribableZones = Set.of(DomainUtil.APP_ZONE_PREFIX + "."
                                               + ((OrganizationParticipant) participant).getOrganizationId());
        }
        return new StompAuthorizer(sendRules, subscribableZones, connectedInfo.getReplyToId());
    }

}
