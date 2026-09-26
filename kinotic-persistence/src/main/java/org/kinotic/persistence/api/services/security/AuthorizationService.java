package org.kinotic.persistence.api.services.security;

import io.vertx.core.Future;
import org.kinotic.persistence.api.model.EntityContext;

/**
 * The {@link AuthorizationService} is responsible for authorizing a given action
 * This is a generic service that can be used to authorize any action
 * Created by Navíd Mitchell 🤪on 12/31/24
 */
public interface AuthorizationService<T> {

    Future<Void> authorize(T operationIdentifier,
                           EntityContext entityContext);

}
