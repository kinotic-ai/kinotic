package org.kinotic.core.internal.api;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.event.EventService;
import org.kinotic.core.api.security.ConnectionRevocation;
import org.kinotic.core.api.security.ConnectionRevocationService;
import org.kinotic.core.api.security.ConnectionRevocationType;
import org.springframework.stereotype.Component;

/**
 * Publishes each revocation with {@link EventService#publish(Object)} to every node with a confirmed consumer of it,
 * so every gateway holding a connection it names ends it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultConnectionRevocationService implements ConnectionRevocationService {

    private final EventService eventService;

    // The state the revocation follows is written already and refuses a reconnect, so the caller is answered as soon
    // as the delivery starts rather than once every node has acknowledged it: a node that does not answer would
    // otherwise hold the caller's request for every retry and then fail it. The event service retries, and a delivery
    // that still fails is logged here.
    @Override
    public Future<Void> revoke(String identityId) {
        Validate.notBlank(identityId, "identityId must not be blank");
        return publish(new ConnectionRevocation(ConnectionRevocationType.IDENTITY, identityId));
    }

    @Override
    public Future<Void> revokeLogin(String loginId) {
        Validate.notBlank(loginId, "loginId must not be blank");
        return publish(new ConnectionRevocation(ConnectionRevocationType.LOGIN, loginId));
    }

    private Future<Void> publish(ConnectionRevocation revocation) {
        eventService.publish(revocation)
                    .onFailure(error -> log.error("The revocation of {} {} was not delivered to every node",
                                                  revocation.getType(), revocation.getId(), error));
        return Future.succeededFuture();
    }

}
