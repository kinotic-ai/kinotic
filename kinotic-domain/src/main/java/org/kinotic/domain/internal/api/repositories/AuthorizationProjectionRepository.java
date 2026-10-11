package org.kinotic.domain.internal.api.repositories;

import org.kinotic.domain.internal.model.security.AuthorizationProjection;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "kinotic.authorization", name = "enabled", havingValue = "true")
public class AuthorizationProjectionRepository extends AbstractRepository<AuthorizationProjection> {
    public AuthorizationProjectionRepository(CrudServiceTemplate template) {
        super("kinotic_authorization_projection", AuthorizationProjection.class, template);
    }
}
