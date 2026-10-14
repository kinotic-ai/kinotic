package org.kinotic.domain.internal.api.repositories;

import org.kinotic.domain.api.model.security.authorization.AuthorizationModel;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "kinotic.authorization", name = "enabled", havingValue = "true")
public class AuthorizationModelRepository extends AbstractRepository<AuthorizationModel> {
    public AuthorizationModelRepository(CrudServiceTemplate template) {
        super("kinotic_authorization_model", AuthorizationModel.class, template);
    }
}
