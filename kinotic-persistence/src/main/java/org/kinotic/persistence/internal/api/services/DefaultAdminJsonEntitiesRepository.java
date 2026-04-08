package org.kinotic.persistence.internal.api.services;

import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.persistence.api.model.FastestType;
import org.kinotic.persistence.api.model.QueryParameter;
import org.kinotic.os.api.model.RawJson;
import org.kinotic.persistence.api.model.TenantSpecificId;
import org.kinotic.persistence.api.services.AdminJsonEntitiesRepository;
import org.kinotic.persistence.api.services.EntitiesRepository;
import org.kinotic.persistence.internal.api.model.DefaultEntityContext;
import org.kinotic.persistence.internal.api.services.sql.ListParameterHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created By Navíd Mitchell 🤪on 2/18/25
 */
@Component
@RequiredArgsConstructor
public class DefaultAdminJsonEntitiesRepository implements AdminJsonEntitiesRepository {

    private final EntitiesRepository entitiesRepository;

    @Override
    public CompletableFuture<Long> count(String entityDefinitionId, List<String> tenantSelection, Participant participant) {
        return entitiesRepository.count(entityDefinitionId,
                                        new DefaultEntityContext(participant)
                                             .setTenantSelection(tenantSelection));
    }

    @Override
    public CompletableFuture<Long> countByQuery(String entityDefinitionId,
                                                String query,
                                                List<String> tenantSelection,
                                                Participant participant) {
        return entitiesRepository.countByQuery(entityDefinitionId,
                                               query,
                                               new DefaultEntityContext(participant)
                                                    .setTenantSelection(tenantSelection));
    }

    @Override
    public CompletableFuture<Void> deleteById(String entityDefinitionId, TenantSpecificId id, Participant participant) {
        return entitiesRepository.deleteById(entityDefinitionId,
                                             id,
                                             new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Void> deleteByQuery(String entityDefinitionId,
                                                 String query,
                                                 List<String> tenantSelection,
                                                 Participant participant) {
        return entitiesRepository.deleteByQuery(entityDefinitionId,
                                                query,
                                                new DefaultEntityContext(participant)
                                                     .setTenantSelection(tenantSelection));
    }

    @Override
    public CompletableFuture<Page<FastestType>> findAll(String entityDefinitionId,
                                                        List<String> tenantSelection,
                                                        Pageable pageable,
                                                        Participant participant) {
        return entitiesRepository.findAll(entityDefinitionId,
                                          pageable,
                                          FastestType.class,
                                          new DefaultEntityContext(participant)
                                               .setTenantSelection(tenantSelection));
    }

    @Override
    public CompletableFuture<FastestType> findById(String entityDefinitionId, TenantSpecificId id, Participant participant) {
        return entitiesRepository.findById(entityDefinitionId,
                                           id,
                                           FastestType.class,
                                           new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<List<FastestType>> findByIds(String entityDefinitionId,
                                                          List<TenantSpecificId> ids,
                                                          Participant participant) {
        return entitiesRepository.findByIdsWithTenant(entityDefinitionId,
                                                      ids,
                                                      FastestType.class,
                                                      new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<List<RawJson>> namedQuery(String entityDefinitionId,
                                                       String queryName,
                                                       List<QueryParameter> queryParameters,
                                                       List<String> tenantSelection,
                                                       Participant participant) {
        return entitiesRepository.namedQuery(entityDefinitionId,
                                             queryName,
                                             new ListParameterHolder(queryParameters),
                                             RawJson.class,
                                             new DefaultEntityContext(participant)
                                                  .setTenantSelection(tenantSelection));
    }

    @Override
    public CompletableFuture<Page<RawJson>> namedQueryPage(String entityDefinitionId,
                                                           String queryName,
                                                           List<QueryParameter> queryParameters,
                                                           List<String> tenantSelection,
                                                           Pageable pageable,
                                                           Participant participant) {
        return entitiesRepository.namedQueryPage(entityDefinitionId,
                                                 queryName,
                                                 new ListParameterHolder(queryParameters),
                                                 pageable,
                                                 RawJson.class,
                                                 new DefaultEntityContext(participant)
                                                      .setTenantSelection(tenantSelection));
    }

    @Override
    public CompletableFuture<Page<FastestType>> search(String entityDefinitionId,
                                                       String searchText,
                                                       List<String> tenantSelection,
                                                       Pageable pageable,
                                                       Participant participant) {
        return entitiesRepository.search(entityDefinitionId,
                                         searchText,
                                         pageable,
                                         FastestType.class,
                                         new DefaultEntityContext(participant)
                                              .setTenantSelection(tenantSelection));
    }

}
