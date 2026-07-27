package org.kinotic.domain.internal.api.repositories;

import org.kinotic.domain.internal.api.model.OAuthClient;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class OAuthClientRepository extends AbstractRepository<OAuthClient> {

    public OAuthClientRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_oauth_client", OAuthClient.class, crudServiceTemplate);
    }
}
