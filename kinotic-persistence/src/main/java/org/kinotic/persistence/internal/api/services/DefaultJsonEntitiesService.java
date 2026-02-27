package org.kinotic.persistence.internal.api.services;

import tools.jackson.databind.util.TokenBuffer;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.services.crud.Page;
import org.kinotic.domain.api.services.crud.Pageable;
import org.kinotic.persistence.internal.api.domain.DefaultEntityContext;
import org.kinotic.persistence.api.model.FastestType;
import org.kinotic.persistence.api.model.QueryParameter;
import org.kinotic.domain.api.model.RawJson;
import org.kinotic.persistence.api.services.EntitiesService;
import org.kinotic.persistence.api.services.JsonEntitiesService;
import org.kinotic.persistence.internal.api.services.sql.ListParameterHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Nic Padilla 🤪on 6/18/23.
 */
@Component
@RequiredArgsConstructor
public class DefaultJsonEntitiesService implements JsonEntitiesService {

    private final EntitiesService entitiesService;

    @Override
    public CompletableFuture<Void> bulkSave(String entityDefinitionId, TokenBuffer entities, Participant participant) {
        return entitiesService.bulkSave(entityDefinitionId, entities, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Void> bulkUpdate(String entityDefinitionId, TokenBuffer entities, Participant participant) {
        return entitiesService.bulkUpdate(entityDefinitionId, entities, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Long> count(String entityDefinitionId, Participant participant) {
        return entitiesService.count(entityDefinitionId, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Long> countByQuery(String entityDefinitionId, String query, Participant participant) {
        return entitiesService.countByQuery(entityDefinitionId, query, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Void> deleteById(String entityDefinitionId, String id, Participant participant) {
        return entitiesService.deleteById(entityDefinitionId, id, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Void> deleteByQuery(String entityDefinitionId, String query, Participant participant) {
        return entitiesService.deleteByQuery(entityDefinitionId, query, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Page<FastestType>> findAll(String entityDefinitionId,
                                                        Pageable pageable,
                                                        Participant participant) {
        return entitiesService.findAll(entityDefinitionId, pageable, FastestType.class, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<FastestType> findById(String entityDefinitionId, String id, Participant participant) {
        return entitiesService.findById(entityDefinitionId, id, FastestType.class, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<List<FastestType>> findByIds(String entityDefinitionId, List<String> ids, Participant participant) {
        return entitiesService.findByIds(entityDefinitionId, ids, FastestType.class, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<List<RawJson>> namedQuery(String entityDefinitionId,
                                                       String queryName,
                                                       List<QueryParameter> queryParameters,
                                                       Participant participant) {
        return entitiesService.namedQuery(entityDefinitionId,
                                          queryName,
                                          new ListParameterHolder(queryParameters),
                                          RawJson.class,
                                          new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Page<RawJson>> namedQueryPage(String entityDefinitionId,
                                                           String queryName,
                                                           List<QueryParameter> queryParameters,
                                                           Pageable pageable,
                                                           Participant participant) {
        return entitiesService.namedQueryPage(entityDefinitionId,
                                              queryName,
                                              new ListParameterHolder(queryParameters),
                                              pageable,
                                              RawJson.class,
                                              new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Void> syncIndex(String entityDefinitionId, Participant participant) {
        return entitiesService.syncIndex(entityDefinitionId, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<TokenBuffer> save(String entityDefinitionId, TokenBuffer entity, Participant participant) {
        return entitiesService.save(entityDefinitionId, entity, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Page<FastestType>> search(String entityDefinitionId,
                                                       String searchText,
                                                       Pageable pageable,
                                                       Participant participant) {
        return entitiesService.search(entityDefinitionId, searchText, pageable, FastestType.class, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<TokenBuffer> update(String entityDefinitionId, TokenBuffer entity, Participant participant) {
        return entitiesService.update(entityDefinitionId, entity, new DefaultEntityContext(participant));
    }

}
