package org.kinotic.persistence.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractProjectScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class EntityDefinitionRepository extends AbstractProjectScopedRepository<EntityDefinition> {

    public EntityDefinitionRepository(ElasticsearchAsyncClient esAsyncClient,
                                      CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_entity_definition", EntityDefinition.class, esAsyncClient, crudServiceTemplate);
    }

    /**
     * Looks up an EntityDefinition by its prefixed id. The id encodes {@code orgId} as its
     * prefix (see
     * {@link org.kinotic.persistence.internal.utils.PersistenceUtil#createEntityDefinitionId}),
     * which this method extracts and forwards to the orgId-aware overload — under the
     * composite-document-id scheme, a lookup without an orgId can't address the row.
     * Returns {@code null} for ids that don't carry the expected prefix.
     */
    public CompletableFuture<EntityDefinition> findById(String id) {
        if (id == null) return CompletableFuture.completedFuture(null);
        int dot = id.indexOf('.');
        if (dot <= 0) return CompletableFuture.completedFuture(null);
        return findById(id, id.substring(0, dot));
    }

    public CompletableFuture<Page<EntityDefinition>> findAllPublishedForApplication(String applicationId,
                                                                                    String orgId,
                                                                                    Pageable pageable) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findAll(pageable,
                       b -> b.routing(orgId).query(composeOrgFilter(orgId,
                                                                    applicationIdFilter(applicationId),
                                                                    termFilter("published", true))));
    }
}
