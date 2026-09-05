package org.kinotic.gateway.internal.endpoints.stomp;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.domain.api.model.security.ZoneRules;
import org.springframework.stereotype.Component;

/**
 * Creates the {@link StompAuthorizer} for a connected participant. Zone authorization is derived from the
 * participant type by {@link ZoneRules}, which documents the full participant routing matrix.
 */
@Component
public class StompAuthorizerFactory {

    public StompAuthorizer create(ConnectedInfo connectedInfo) {
        Validate.notNull(connectedInfo, "connectedInfo must not be null");
        Validate.notNull(connectedInfo.getParticipant(), "participant must not be null");
        Validate.notEmpty(connectedInfo.getReplyToId(), "replyToId must not be empty");
        return new StompAuthorizer(ZoneRules.from(connectedInfo.getParticipant()), connectedInfo.getReplyToId());
    }

}
