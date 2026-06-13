package org.kinotic.persistence.internal.api.services;

import tools.jackson.databind.util.TokenBuffer;
import lombok.RequiredArgsConstructor;
import org.kinotic.domain.api.security.ApplicationParticipant;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.persistence.internal.api.model.DefaultEntityContext;
import org.kinotic.persistence.api.model.FastestType;
import org.kinotic.persistence.api.model.QueryParameter;
import org.kinotic.domain.api.model.RawJson;
import org.kinotic.persistence.api.services.JsonEntitiesRepository;
import org.kinotic.persistence.internal.api.services.sql.ListParameterHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Nic Padilla 🤪on 6/18/23.
 */
@Component
@RequiredArgsConstructor
public class DefaultJsonEntitiesRepository implements JsonEntitiesRepository {

    private final EntitiesService entitiesService;

    @Override
    public CompletableFuture<Void> bulkSave(String entityDefinitionId, TokenBuffer entities, ApplicationParticipant participant) {
        return entitiesService.bulkSave(entityDefinitionId, entities, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Void> bulkUpdate(String entityDefinitionId, TokenBuffer entities, ApplicationParticipant participant) {
        return entitiesService.bulkUpdate(entityDefinitionId, entities, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Long> count(String entityDefinitionId, ApplicationParticipant participant) {
        return entitiesService.count(entityDefinitionId, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Long> countByQuery(String entityDefinitionId, String query, ApplicationParticipant participant) {
        return entitiesService.countByQuery(entityDefinitionId, query, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Void> deleteById(String entityDefinitionId, String id, ApplicationParticipant participant) {
        return entitiesService.deleteById(entityDefinitionId, id, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Void> deleteByQuery(String entityDefinitionId, String query, ApplicationParticipant participant) {
        return entitiesService.deleteByQuery(entityDefinitionId, query, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Page<FastestType>> findAll(String entityDefinitionId,
                                                        Pageable pageable,
                                                        ApplicationParticipant participant) {
        return entitiesService.findAll(entityDefinitionId, pageable, FastestType.class, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<FastestType> findById(String entityDefinitionId, String id, ApplicationParticipant participant) {
        return entitiesService.findById(entityDefinitionId, id, FastestType.class, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<List<FastestType>> findByIds(String entityDefinitionId, List<String> ids, ApplicationParticipant participant) {
        return entitiesService.findByIds(entityDefinitionId, ids, FastestType.class, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<List<RawJson>> namedQuery(String entityDefinitionId,
                                                       String queryName,
                                                       List<QueryParameter> queryParameters,
                                                       ApplicationParticipant participant) {
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
                                                           ApplicationParticipant participant) {
        return entitiesService.namedQueryPage(entityDefinitionId,
                                              queryName,
                                              new ListParameterHolder(queryParameters),
                                              pageable,
                                              RawJson.class,
                                              new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Void> syncIndex(String entityDefinitionId, ApplicationParticipant participant) {
        return entitiesService.syncIndex(entityDefinitionId, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<TokenBuffer> save(String entityDefinitionId, TokenBuffer entity, ApplicationParticipant participant) {
        return entitiesService.save(entityDefinitionId, entity, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Page<FastestType>> search(String entityDefinitionId,
                                                       String searchText,
                                                       Pageable pageable,
                                                       ApplicationParticipant participant) {
        return entitiesService.search(entityDefinitionId, searchText, pageable, FastestType.class, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<TokenBuffer> update(String entityDefinitionId, TokenBuffer entity, ApplicationParticipant participant) {
        return entitiesService.update(entityDefinitionId, entity, new DefaultEntityContext(participant));
    }

}
