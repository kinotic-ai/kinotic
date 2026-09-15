package org.kinotic.persistence.internal.api.services;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.TenantSpecificId;
import org.kinotic.persistence.internal.cache.events.CacheEvictionEvent;
import org.kinotic.persistence.internal.cache.events.EvictionSourceType;
import org.kinotic.persistence.api.model.ParameterHolder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Created by Navíd Mitchell 🤪on 5/10/23.
 */
@Slf4j
@Component
public class DefaultEntitiesService implements EntitiesService {

    private final EntityServiceCache entityServiceCache;

    public DefaultEntitiesService(EntityServiceCache entityServiceCache) {
        this.entityServiceCache = entityServiceCache;
    }

    /**
     * Evicts the caches for a given {@link EntityDefinition}, this is used when a {@link EntityDefinition} is updated on a remote node.
     * @param event the event containing the {@link EntityDefinition} to evict the caches for
     */
    @EventListener
    public void handleEntityDefinitionCacheEviction(CacheEvictionEvent event) {

        try {

            if(event.getEvictionSourceType() == EvictionSourceType.ENTITY_DEFINITION){
                this.entityServiceCache.evict(event.getOrganizationId(), event.getEntityDefinitionId());
            }

        } catch (Exception e) {
            log.error("failed to handle EntityDefinition cache eviction (source: {})",
                     event.getEvictionSource().getDisplayName(), e);
        }
    }

    @WithSpan
    @Override
    public <T> Future<Void> bulkSave(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                     T entities,
                                     EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.bulkSave(entities, context));
    }

    @WithSpan
    @Override
    public <T> Future<Void> bulkUpdate(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                       T entities,
                                       EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.bulkUpdate(entities, context));
    }

    @WithSpan
    @Override
    public Future<Long> count(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                              EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.count(context));
    }

    @WithSpan
    @Override
    public Future<Long> countByQuery(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                     String query,
                                     EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.countByQuery(query, context));
    }

    @WithSpan
    @Override
    public Future<Void> deleteById(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                   String id,
                                   EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.deleteById(id, context));
    }

    @Override
    public Future<Void> deleteById(String entityDefinitionId, TenantSpecificId id, EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.deleteById(id, context));
    }

    @WithSpan
    @Override
    public Future<Void> deleteByQuery(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                      String query,
                                      EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.deleteByQuery(query, context));
    }

    @WithSpan
    @Override
    public <T> Future<Page<T>> findAll(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                       Pageable pageable,
                                       Class<T> type,
                                       EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.findAll(pageable, type, context));
    }

    @WithSpan
    @Override
    public <T> Future<T> findById(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                  String id,
                                  Class<T> type,
                                  EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.findById(id, type, context));
    }

    @Override
    public <T> Future<T> findById(String entityDefinitionId, TenantSpecificId id, Class<T> type, EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.findById(id, type, context));
    }

    @WithSpan
    @Override
    public <T> Future<List<T>> findByIds(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                         List<String> ids,
                                         Class<T> type,
                                         EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.findByIds(ids, type, context));
    }

    @Override
    public <T> Future<List<T>> findByIdsWithTenant(String entityDefinitionId,
                                                   List<TenantSpecificId> ids,
                                                   Class<T> type,
                                                   EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.findByIdsWithTenant(ids, type, context));
    }

    @WithSpan
    @Override
    public <T> Future<List<T>> namedQuery(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                          @SpanAttribute("queryName") String queryName,
                                          ParameterHolder parameterHolder,
                                          Class<T> type,
                                          EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.namedQuery(queryName, parameterHolder, type, context));
    }

    @WithSpan
    @Override
    public <T> Future<Page<T>> namedQueryPage(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                              @SpanAttribute("queryName") String queryName,
                                              ParameterHolder parameterHolder,
                                              Pageable pageable,
                                              Class<T> type,
                                              EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.namedQueryPage(queryName,
                                                                       parameterHolder,
                                                                       pageable,
                                                                       type,
                                                                       context));
    }

    @Override
    public Future<Void> syncIndex(String entityDefinitionId,
                                  EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.syncIndex(context));
    }

    @WithSpan
    @Override
    public <T> Future<T> save(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                              T entity,
                              EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.save(entity, context));
    }

    @WithSpan
    @Override
    public <T> Future<Page<T>> search(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                      String searchText,
                                      Pageable pageable,
                                      Class<T> type,
                                      EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.search(searchText, pageable, type, context));
    }

    @WithSpan
    @Override
    public <T> Future<T> update(@SpanAttribute("entityDefinitionId") String entityDefinitionId,
                                T entity,
                                EntityContext context) {
        return entityServiceCache.get(context.getOrganizationId(), entityDefinitionId)
                .compose(entityService -> entityService.update(entity, context));
    }

}
