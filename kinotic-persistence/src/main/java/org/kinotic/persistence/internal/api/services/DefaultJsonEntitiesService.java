package org.kinotic.persistence.internal.api.services;

import tools.jackson.databind.util.TokenBuffer;
import lombok.RequiredArgsConstructor;
import org.kinotic.rpc.api.security.Participant;
import org.kinotic.rpc.api.crud.Page;
import org.kinotic.rpc.api.crud.Pageable;
import org.kinotic.persistence.internal.api.domain.DefaultEntityContext;
import org.kinotic.persistence.api.domain.FastestType;
import org.kinotic.persistence.api.domain.QueryParameter;
import org.kinotic.persistence.api.domain.RawJson;
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
    public CompletableFuture<Void> bulkSave(String structureId, TokenBuffer entities, Participant participant) {
        return entitiesService.bulkSave(structureId, entities, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Void> bulkUpdate(String structureId, TokenBuffer entities, Participant participant) {
        return entitiesService.bulkUpdate(structureId, entities, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Long> count(String structureId, Participant participant) {
        return entitiesService.count(structureId, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Long> countByQuery(String structureId, String query, Participant participant) {
        return entitiesService.countByQuery(structureId, query, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Void> deleteById(String structureId, String id, Participant participant) {
        return entitiesService.deleteById(structureId, id, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Void> deleteByQuery(String structureId, String query, Participant participant) {
        return entitiesService.deleteByQuery(structureId, query, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Page<FastestType>> findAll(String structureId,
                                                        Pageable pageable,
                                                        Participant participant) {
        return entitiesService.findAll(structureId, pageable, FastestType.class, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<FastestType> findById(String structureId, String id, Participant participant) {
        return entitiesService.findById(structureId, id, FastestType.class, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<List<FastestType>> findByIds(String structureId, List<String> ids, Participant participant) {
        return entitiesService.findByIds(structureId, ids, FastestType.class, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<List<RawJson>> namedQuery(String structureId,
                                                       String queryName,
                                                       List<QueryParameter> queryParameters,
                                                       Participant participant) {
        return entitiesService.namedQuery(structureId,
                                          queryName,
                                          new ListParameterHolder(queryParameters),
                                          RawJson.class,
                                          new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Page<RawJson>> namedQueryPage(String structureId,
                                                           String queryName,
                                                           List<QueryParameter> queryParameters,
                                                           Pageable pageable,
                                                           Participant participant) {
        return entitiesService.namedQueryPage(structureId,
                                              queryName,
                                              new ListParameterHolder(queryParameters),
                                              pageable,
                                              RawJson.class,
                                              new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Void> syncIndex(String structureId, Participant participant) {
        return entitiesService.syncIndex(structureId, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<TokenBuffer> save(String structureId, TokenBuffer entity, Participant participant) {
        return entitiesService.save(structureId, entity, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<Page<FastestType>> search(String structureId,
                                                       String searchText,
                                                       Pageable pageable,
                                                       Participant participant) {
        return entitiesService.search(structureId, searchText, pageable, FastestType.class, new DefaultEntityContext(participant));
    }

    @Override
    public CompletableFuture<TokenBuffer> update(String structureId, TokenBuffer entity, Participant participant) {
        return entitiesService.update(structureId, entity, new DefaultEntityContext(participant));
    }

}
