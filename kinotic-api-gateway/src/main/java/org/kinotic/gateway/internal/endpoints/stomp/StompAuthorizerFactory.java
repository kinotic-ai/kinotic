package org.kinotic.gateway.internal.endpoints.stomp;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.event.ZonePartitioningService;
import org.kinotic.core.api.event.ZoneRulesResolver;
import org.kinotic.core.api.security.ConnectedInfo;
import org.springframework.stereotype.Component;

/**
 * Creates the {@link StompAuthorizer} for a connected participant. Zone authorization is what the server's
 * {@link ZoneRulesResolver} derives for the participant, narrowed to the zones this server's
 * {@link ZonePartitioningService} serves.
 */
@Component
@RequiredArgsConstructor
public class StompAuthorizerFactory {

    private final ZonePartitioningService zonePartitioningService;
    private final ZoneRulesResolver zoneRulesResolver;

    public Future<StompAuthorizer> create(ConnectedInfo connectedInfo) {
        Validate.notNull(connectedInfo, "connectedInfo must not be null");
        Validate.notNull(connectedInfo.getParticipant(), "participant must not be null");
        Validate.notEmpty(connectedInfo.getReplyToId(), "replyToId must not be empty");
        return zoneRulesResolver.resolve(connectedInfo.getParticipant())
                                .map(zoneRules -> new StompAuthorizer(zoneRules.restrictedTo(zonePartitioningService),
                                                                      connectedInfo.getReplyToId()));
    }

}
