package org.kinotic.gateway.internal.endpoints.stomp;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.event.ZonePartition;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.domain.api.model.security.ZoneRules;
import org.springframework.stereotype.Component;

/**
 * Creates the {@link StompAuthorizer} for a connected participant. Zone authorization is derived from the
 * participant type by {@link ZoneRules}, which documents the full participant routing matrix, narrowed to the
 * zones this server's {@link ZonePartition} serves.
 */
@Component
@RequiredArgsConstructor
public class StompAuthorizerFactory {

    private final ZonePartition zonePartition;

    public StompAuthorizer create(ConnectedInfo connectedInfo) {
        Validate.notNull(connectedInfo, "connectedInfo must not be null");
        Validate.notNull(connectedInfo.getParticipant(), "participant must not be null");
        Validate.notEmpty(connectedInfo.getReplyToId(), "replyToId must not be empty");
        return new StompAuthorizer(ZoneRules.from(connectedInfo.getParticipant()).restrictedTo(zonePartition),
                                   connectedInfo.getReplyToId());
    }

}
