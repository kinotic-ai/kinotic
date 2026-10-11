package org.kinotic.domain.internal.api.repositories;

import org.kinotic.domain.api.model.security.authorization.AuthorizationPolicy;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;
import io.vertx.core.Future;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Page;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "kinotic.authorization", name = "enabled", havingValue = "true")
public class AuthorizationPolicyRepository extends AbstractRepository<AuthorizationPolicy> {
    public AuthorizationPolicyRepository(CrudServiceTemplate template) {
        super("kinotic_authorization_policy", AuthorizationPolicy.class, template);
    }
    public Future<Page<AuthorizationPolicy>> findForApplication(String organizationId, String applicationId, Pageable page) {
        return findAll(page, b -> b.query(composeFilter(termFilter("scope.organizationId", organizationId),
                applicationId == null ? missingFilter("scope.applicationId") : termFilter("scope.applicationId", applicationId))));
    }
}
