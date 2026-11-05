package org.kinotic.core.internal.api;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.event.ClusterEventPublisher;
import org.kinotic.core.api.security.ConnectionRevocation;
import org.kinotic.core.api.security.ConnectionRevocationService;
import org.kinotic.core.api.security.ConnectionRevocationType;
import org.springframework.stereotype.Component;

/**
 * Delivers each revocation to every node through the {@link ClusterEventPublisher}, so every gateway holding a
 * connection it names ends it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultConnectionRevocationService implements ConnectionRevocationService {

    private final ClusterEventPublisher clusterEventPublisher;

    @Override
    public Future<Void> revokeIdentity(String identityId) {
        return publish(ConnectionRevocationType.IDENTITY, identityId);
    }

    @Override
    public Future<Void> revokeLogin(String loginId) {
        return publish(ConnectionRevocationType.LOGIN, loginId);
    }

    // The state the revocation follows is written already, so a delivery the retries could not complete fails the
    // caller: what remains undelivered is logged by the publisher, and a repeat of the caller's request delivers again
    private Future<Void> publish(ConnectionRevocationType type, String id) {
        Validate.notBlank(id, "id must not be blank");
        return clusterEventPublisher.publish(new ConnectionRevocation(type, id));
    }

}
