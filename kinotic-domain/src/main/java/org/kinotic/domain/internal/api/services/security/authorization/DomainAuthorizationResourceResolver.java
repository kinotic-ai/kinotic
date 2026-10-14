package org.kinotic.domain.internal.api.services.security.authorization;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.domain.api.model.security.authorization.AuthorizationScope;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.repositories.EntityDefinitionRepository;
import org.kinotic.domain.api.services.security.authorization.AuthorizationResourceResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kinotic.authorization", name = "enabled", havingValue = "true")
public class DomainAuthorizationResourceResolver implements AuthorizationResourceResolver {
    private final ApplicationRepository applications;
    private final EntityDefinitionRepository entities;
    public boolean supports(String type) { return type.equals("application") || type.equals("entity_definition"); }
    public Future<Void> requireOwned(String type, String id, AuthorizationScope scope) {
        if (type.equals("application")) return applications.findById(id, scope.getOrganizationId()).compose(value -> {
            if (value == null || scope.getApplicationId() != null && !scope.getApplicationId().equals(id)) return denied();
            return Future.succeededFuture();
        });
        return entities.findById(id, scope.getOrganizationId()).compose(value -> {
            if (value == null || scope.getApplicationId() != null && !Objects.equals(scope.getApplicationId(), value.getApplicationId())) return denied();
            return Future.succeededFuture();
        });
    }
    private Future<Void> denied() { return Future.failedFuture(new AuthorizationException("Resource is outside this scope")); }
}
