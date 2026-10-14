package org.kinotic.domain.internal.api.services.security.authorization;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.domain.api.config.OpenFgaProperties;
import org.kinotic.domain.api.model.security.authorization.*;
import org.kinotic.domain.api.model.security.participant.*;
import org.kinotic.domain.api.services.security.authorization.PermissionAuthorizationService;
import org.kinotic.domain.internal.api.repositories.AuthorizationModelRepository;
import org.kinotic.domain.internal.model.security.AuthorizationTuple;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kinotic.authorization", name = "enabled", havingValue = "true")
public class DefaultPermissionAuthorizationService implements PermissionAuthorizationService {
    private final AuthorizationModelRepository models;
    private final OpenFgaClient client;
    private final OpenFgaProperties properties;
    private final Vertx vertx;

    public boolean bootstrap(ScopedParticipant participant) {
        return participant instanceof SystemParticipant && properties.getBootstrapSystemIdentityIds().contains(participant.getId());
    }
    public static AuthorizationScope scope(ScopedParticipant participant) {
        return AuthorizationScope.from(participant);
    }
    public <T> Future<T> bounded(Supplier<Future<T>> task) {
        Promise<T> promise = Promise.promise();
        long deadline = System.nanoTime() + properties.getCheckTimeout().toNanos();
        long timer = vertx.setTimer(properties.getCheckTimeout().toMillis(), ignored -> promise.tryFail(new IllegalStateException("Authorization deadline exceeded")));
        try {
            task.get().onComplete(result -> {
                vertx.cancelTimer(timer);
                if (System.nanoTime() >= deadline) promise.tryFail(new IllegalStateException("Authorization deadline exceeded"));
                else if (result.succeeded()) promise.tryComplete(result.result());
                else promise.tryFail(result.cause());
            });
        } catch (Exception e) { vertx.cancelTimer(timer); promise.tryFail(e); }
        return promise.future();
    }
    @Override
    public Future<Void> require(ScopedParticipant participant, AuthorizationScope scope, String type, String id, String permission) {
        return bounded(() -> {
            if (bootstrap(participant)) return Future.succeededFuture();
            if (participant == null || participant instanceof SystemParticipant || !scope.key().equals(scope(participant).key())) return Future.failedFuture(new AuthorizationException("Access denied"));
            AuthorizationCompiler.validatePermission(new AuthorizationPermission().setPermission(permission).setResourceType(type));
            String target = id == null ? "*" : id;
            String modelKey = scope.applicationScope().key();
            return models.findById(modelKey).compose(model -> {
                if (model == null || model.getStoreId() == null || model.getModelId() == null) return Future.failedFuture(new AuthorizationException("Access is not configured"));
                if (model.isPending()) return Future.failedFuture(new IllegalStateException("Authorization publication is pending"));
                return client.check(model.getStoreId(), model.getModelId(), new AuthorizationTuple(AuthorizationCompiler.user(participant.getId()), "access",
                        AuthorizationCompiler.resource(scope, type, target, permission)), AuthorizationCompiler.parents(scope, type, target, permission)).compose(allowed -> {
                    if (!allowed) return Future.failedFuture(new AuthorizationException("Access denied"));
                    return models.findById(modelKey).compose(current -> {
                        if (current == null || current.isPending() || current.getRevision() != model.getRevision()
                                || !Objects.equals(current.getModelId(), model.getModelId())) return Future.failedFuture(new IllegalStateException("Authorization changed during the request"));
                        return Future.succeededFuture();
                    });
                });
            });
        });
    }
}
