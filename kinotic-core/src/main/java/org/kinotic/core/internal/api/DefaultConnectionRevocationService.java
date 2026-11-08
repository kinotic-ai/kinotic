package org.kinotic.core.internal.api;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.event.EventService;
import org.kinotic.core.api.security.ConnectionRevocation;
import org.kinotic.core.api.security.ConnectionRevocationService;
import org.springframework.stereotype.Component;

/**
 * Publishes each revocation with {@link EventService#publishConfirmed(Object)} to every node consuming it, so every
 * gateway holding a connection it names ends it.
 */
@Component
@RequiredArgsConstructor
public class DefaultConnectionRevocationService implements ConnectionRevocationService {

    private final EventService eventService;

    // The state the revocation follows is written already and refuses a reconnect, so the caller is answered as soon
    // as the delivery starts rather than once every node has acknowledged it: a node that does not answer would
    // otherwise hold the caller's request for every retry and then fail it. The event service retries, and logs what it
    // could not deliver.
    @Override
    public Future<Void> revoke(String identityId) {
        Validate.notBlank(identityId, "identityId must not be blank");
        eventService.publishConfirmed(new ConnectionRevocation(identityId));
        return Future.succeededFuture();
    }

}
