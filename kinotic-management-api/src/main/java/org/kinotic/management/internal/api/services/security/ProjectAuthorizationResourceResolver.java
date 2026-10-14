package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.domain.api.model.security.authorization.AuthorizationScope;
import org.kinotic.domain.api.services.security.authorization.AuthorizationResourceResolver;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kinotic.authorization", name = "enabled", havingValue = "true")
public class ProjectAuthorizationResourceResolver implements AuthorizationResourceResolver {
    private final ProjectRepository projects;
    public boolean supports(String type) { return type.equals("project"); }
    public Future<Void> requireOwned(String type, String id, AuthorizationScope scope) {
        return projects.findById(id, scope.getOrganizationId()).compose(value -> {
            if (value == null || scope.getApplicationId() != null && !Objects.equals(scope.getApplicationId(), value.getApplicationId())) return Future.failedFuture(new AuthorizationException("Resource is outside this scope"));
            return Future.succeededFuture();
        });
    }
}
