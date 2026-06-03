package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.kinotic.domain.api.model.iam.OidcPendingSignUp;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class OidcPendingSignUpRepository extends PendingSignUpRepository<OidcPendingSignUp> {

    public OidcPendingSignUpRepository(ElasticsearchAsyncClient esAsyncClient,
                                       CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_oidc_pending_signup", OidcPendingSignUp.class, esAsyncClient, crudServiceTemplate);
    }
}
