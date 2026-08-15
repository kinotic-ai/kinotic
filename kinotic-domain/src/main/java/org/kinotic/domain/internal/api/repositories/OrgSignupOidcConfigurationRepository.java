package org.kinotic.domain.internal.api.repositories;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.OidcProviderKind;
import org.kinotic.domain.api.model.security.OrgSignupOidcConfiguration;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrgSignupOidcConfigurationRepository extends AbstractRepository<OrgSignupOidcConfiguration> {

    public OrgSignupOidcConfigurationRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_org_signup_oidc_configuration", OrgSignupOidcConfiguration.class, crudServiceTemplate);
    }

    public Future<List<OrgSignupOidcConfiguration>> findAllEnabled() {
        return findAll(Pageable.ofSize(100), b -> b.query(termFilter("enabled", true)))
                .map(Page::getContent);
    }

    public Future<OrgSignupOidcConfiguration> findEnabledByProvider(OidcProviderKind provider) {
        return findFirst(b -> b.query(composeFilter(
                termFilter("provider", provider.key()),
                termFilter("enabled", true))));
    }
}
