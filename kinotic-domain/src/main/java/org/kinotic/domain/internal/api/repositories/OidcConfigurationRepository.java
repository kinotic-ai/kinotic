package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.query_dsl.IdsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OidcConfigurationRepository extends AbstractOrganizationScopedRepository<OidcConfiguration> {

    public OidcConfigurationRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_oidc_configuration", OidcConfiguration.class, crudServiceTemplate);
    }

    /**
     * Returns the configurations among {@code ids} that belong to {@code orgId} and whose
     * {@code enabled} flag is true.
     */
    public Future<List<OidcConfiguration>> findEnabledByIds(List<String> ids, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        Validate.notEmpty(ids, "ids cannot be null or empty");
        List<String> documentIds = ids.stream()
                                      .map(id -> composeDocumentId(id, orgId))
                                      .toList();
        Query query = composeOrgFilter(orgId,
                                       IdsQuery.of(i -> i.values(documentIds))._toQuery(),
                                       termFilter("enabled", true));
        return findAll(Pageable.ofSize(ids.size()), b -> b.routing(orgId).query(query))
                .map(Page::getContent);
    }
}
