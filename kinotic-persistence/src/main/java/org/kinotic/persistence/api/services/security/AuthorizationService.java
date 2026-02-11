package org.kinotic.persistence.api.services.security;

import org.kinotic.structures.api.domain.SecurityContext;

import java.util.concurrent.CompletableFuture;

/**
 * The {@link AuthorizationService} is responsible for authorizing a given action
 * This is a generic service that can be used to authorize any action
 * Created by Navíd Mitchell 🤪on 12/31/24
 */
public interface AuthorizationService<T> {

    CompletableFuture<Void> authorize(T operationIdentifier,
                                      SecurityContext securityContext);

}
