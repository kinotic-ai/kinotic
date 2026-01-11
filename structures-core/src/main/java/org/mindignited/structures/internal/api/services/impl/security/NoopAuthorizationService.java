package org.mindignited.structures.internal.api.services.impl.security;

import org.mindignited.structures.api.domain.SecurityContext;
import org.mindignited.structures.api.services.security.AuthorizationService;

import java.util.concurrent.CompletableFuture;

public class NoopAuthorizationService<T> implements AuthorizationService<T> {

    @Override
    public CompletableFuture<Void> authorize(T operationIdentifier, SecurityContext securityContext) {
        return CompletableFuture.completedFuture(null);
    }
}
