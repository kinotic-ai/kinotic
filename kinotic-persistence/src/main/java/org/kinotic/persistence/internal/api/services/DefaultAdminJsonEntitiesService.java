package org.kinotic.persistence.internal.api.services;

import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.persistence.api.model.FastestType;
import org.kinotic.persistence.api.model.QueryParameter;
import org.kinotic.os.api.model.RawJson;
import org.kinotic.persistence.api.model.TenantSpecificId;
import org.kinotic.persistence.api.services.AdminJsonEntitiesService;
import org.kinotic.persistence.api.services.EntitiesService;
import org.kinotic.persistence.internal.api.domain.DefaultEntityContext;
import org.kinotic.persistence.internal.api.services.sql.ListParameterHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created By Navíd Mitchell 🤪on 2/18/25
 */
@Component
@RequiredArgsConstructor
public class DefaultAdminJsonEntitiesService implements AdminJsonEntitiesService {

    private final EntitiesService entitiesService;

    @Override
    public CompletableFuture<Long> count(String entityDefinitionId, List<String> tenantSelection, Participant participant) {
        return entitiesService.count(entityDefinitionId,
                                     new DefaultEntityContext(participant)
                                             .setTenantSelection(tenantSelection));
    }

    @Override
    public CompletableFuture<Long> countByQuery(String entityDefinitionId,
                                                String query,
                                                List<String> tenantSelection,
                                                Participant participant) {
        return entitiesService.countByQuery(entityDefinitionId,
                                            query,
                                            new DefaultEntityContext(participant)
                                                    .setTenantSelection(tenantSelection));
    }

    @Override
    public CompletableFuture<Void> deleteById(String entityDefinitionId, TenantSpecificId id, Participant participant) {
        return entitiesService.deleteById(entityDefinitionId,
                                          id,
                                          new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Void> deleteByQuery(String entityDefinitionId,
                                                 String query,
                                                 List<String> tenantSelection,
                                                 Participant participant) {
        return entitiesService.deleteByQuery(entityDefinitionId,
                                             query,
                                             new DefaultEntityContext(participant)
                                                     .setTenantSelection(tenantSelection));
    }

    @Override
    public CompletableFuture<Page<FastestType>> findAll(String entityDefinitionId,
                                                        List<String> tenantSelection,
                                                        Pageable pageable,
                                                        Participant participant) {
        return entitiesService.findAll(entityDefinitionId,
                                       pageable,
                                       FastestType.class,
                                       new DefaultEntityContext(participant)
                                               .setTenantSelection(tenantSelection));
    }

    @Override
    public CompletableFuture<FastestType> findById(String entityDefinitionId, TenantSpecificId id, Participant participant) {
        return entitiesService.findById(entityDefinitionId,
                                        id,
                                        FastestType.class,
                                        new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<List<FastestType>> findByIds(String entityDefinitionId,
                                                          List<TenantSpecificId> ids,
                                                          Participant participant) {
        return entitiesService.findByIdsWithTenant(entityDefinitionId,
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
        return entitiesService.namedQuery(entityDefinitionId,
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
        return entitiesService.namedQueryPage(entityDefinitionId,
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
        return entitiesService.search(entityDefinitionId,
                                      searchText,
                                      pageable,
                                      FastestType.class,
                                      new DefaultEntityContext(participant)
                                              .setTenantSelection(tenantSelection));
    }

}
