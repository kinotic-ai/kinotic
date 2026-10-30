package org.kinotic.core.internal.api;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.ConnectionRevocation;
import org.kinotic.core.api.security.ConnectionRevocationService;
import org.kinotic.core.api.security.ConnectionRevocationType;
import org.springframework.stereotype.Component;

/**
 * Publishes each revocation on the clustered Vert.x event bus, where every gateway listens; the address is not a
 * CRI, so no client can publish one.
 */
@Component
@RequiredArgsConstructor
public class DefaultConnectionRevocationService implements ConnectionRevocationService {

    private final Vertx vertx;

    @Override
    public Future<Void> revokeIdentity(String identityId) {
        return publish(ConnectionRevocationType.IDENTITY, identityId);
    }

    @Override
    public Future<Void> revokeLogin(String loginId) {
        return publish(ConnectionRevocationType.LOGIN, loginId);
    }

    private Future<Void> publish(ConnectionRevocationType type, String id) {
        Validate.notBlank(id, "id must not be blank");
        vertx.eventBus().publish(ConnectionRevocation.ADDRESS, new ConnectionRevocation(type, id).toJson());
        return Future.succeededFuture();
    }

}
