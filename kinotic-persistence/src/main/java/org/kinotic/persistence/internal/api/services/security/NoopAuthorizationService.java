package org.kinotic.persistence.internal.api.services.security;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.persistence.EntityContext;
import org.kinotic.persistence.api.services.security.AuthorizationService;

public class NoopAuthorizationService<T> implements AuthorizationService<T> {

    @Override
    public Future<Void> authorize(T operationIdentifier, EntityContext entityContext) {
        return Future.succeededFuture();
    }
}
