package org.kinotic.persistence.internal.api.services;

import lombok.RequiredArgsConstructor;
import org.kinotic.rpc.api.security.Participant;
import org.kinotic.rpc.api.crud.Page;
import org.kinotic.rpc.api.crud.Pageable;
import org.kinotic.persistence.api.domain.FastestType;
import org.kinotic.persistence.api.domain.QueryParameter;
import org.kinotic.persistence.api.domain.RawJson;
import org.kinotic.persistence.api.domain.TenantSpecificId;
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
    public CompletableFuture<Long> count(String structureId, List<String> tenantSelection, Participant participant) {
        return entitiesService.count(structureId,
                                     new DefaultEntityContext(participant)
                                             .setTenantSelection(tenantSelection));
    }

    @Override
    public CompletableFuture<Long> countByQuery(String structureId,
                                                String query,
                                                List<String> tenantSelection,
                                                Participant participant) {
        return entitiesService.countByQuery(structureId,
                                            query,
                                            new DefaultEntityContext(participant)
                                                    .setTenantSelection(tenantSelection));
    }

    @Override
    public CompletableFuture<Void> deleteById(String structureId, TenantSpecificId id, Participant participant) {
        return entitiesService.deleteById(structureId,
                                          id,
                                          new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Void> deleteByQuery(String structureId,
                                                 String query,
                                                 List<String> tenantSelection,
                                                 Participant participant) {
        return entitiesService.deleteByQuery(structureId,
                                             query,
                                             new DefaultEntityContext(participant)
                                                     .setTenantSelection(tenantSelection));
    }

    @Override
    public CompletableFuture<Page<FastestType>> findAll(String structureId,
                                                        List<String> tenantSelection,
                                                        Pageable pageable,
                                                        Participant participant) {
        return entitiesService.findAll(structureId,
                                       pageable,
                                       FastestType.class,
                                       new DefaultEntityContext(participant)
                                               .setTenantSelection(tenantSelection));
    }

    @Override
    public CompletableFuture<FastestType> findById(String structureId, TenantSpecificId id, Participant participant) {
        return entitiesService.findById(structureId,
                                        id,
                                        FastestType.class,
                                        new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<List<FastestType>> findByIds(String structureId,
                                                          List<TenantSpecificId> ids,
                                                          Participant participant) {
        return entitiesService.findByIdsWithTenant(structureId,
                                                   ids,
                                                   FastestType.class,
                                                   new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<List<RawJson>> namedQuery(String structureId,
                                                       String queryName,
                                                       List<QueryParameter> queryParameters,
                                                       List<String> tenantSelection,
                                                       Participant participant) {
        return entitiesService.namedQuery(structureId,
                                          queryName,
                                          new ListParameterHolder(queryParameters),
                                          RawJson.class,
                                          new DefaultEntityContext(participant)
                                                  .setTenantSelection(tenantSelection));
    }

    @Override
    public CompletableFuture<Page<RawJson>> namedQueryPage(String structureId,
                                                           String queryName,
                                                           List<QueryParameter> queryParameters,
                                                           List<String> tenantSelection,
                                                           Pageable pageable,
                                                           Participant participant) {
        return entitiesService.namedQueryPage(structureId,
                                              queryName,
                                              new ListParameterHolder(queryParameters),
                                              pageable,
                                              RawJson.class,
                                              new DefaultEntityContext(participant)
                                                      .setTenantSelection(tenantSelection));
    }

    @Override
    public CompletableFuture<Page<FastestType>> search(String structureId,
                                                       String searchText,
                                                       List<String> tenantSelection,
                                                       Pageable pageable,
                                                       Participant participant) {
        return entitiesService.search(structureId,
                                      searchText,
                                      pageable,
                                      FastestType.class,
                                      new DefaultEntityContext(participant)
                                              .setTenantSelection(tenantSelection));
    }

}
