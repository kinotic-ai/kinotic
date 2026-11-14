package org.kinotic.persistence.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.OpType;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.mget.MultiGetOperation;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.domain.api.model.RawJson;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.persistence.api.model.*;
import org.kinotic.domain.api.model.persistence.*;
import org.kinotic.domain.api.model.persistence.idl.decorators.MultiTenancyType;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.persistence.api.services.NamedQueriesService;
import org.kinotic.persistence.api.services.security.AuthorizationService;
import org.kinotic.persistence.internal.api.hooks.ReadPostProcessor;
import org.kinotic.persistence.internal.api.hooks.ReadPreProcessor;
import org.kinotic.persistence.internal.api.hooks.impl.TokenBufferUpsertPreProcessor;
import org.kinotic.persistence.internal.utils.PersistenceUtil;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.util.TokenBuffer;

import java.util.*;
import java.util.function.Function;

/**
 * Created by Navíd Mitchell 🤪on 5/2/23.
 */
@RequiredArgsConstructor
public class DefaultEntityService implements EntityService {

    private final AuthorizationService<EntityOperation> authService;
    private final CrudServiceTemplate crudServiceTemplate;
    private final TokenBufferUpsertPreProcessor upsertPreProcessor;
    private final ElasticsearchAsyncClient esAsyncClient;
    private final NamedQueriesService namedQueriesService;
    private final ObjectMapper objectMapper;
    private final ReadPreProcessor readPreProcessor;
    private final ReadPostProcessor readPostProcessor;
    private final EntityVersionWriter versionWriter;
    private final EntityDescriptor entityDescriptor;

    @WithSpan
    @Override
    public Future<Void> bulkSave(TokenBuffer entities, EntityContext context) {
        return doPersistBulk(entities,
                             EntityOperation.BULK_SAVE,
                             context,
                             entityHolder -> BulkOperation.of(b -> {
                                 // When optimistic locking is enabled and no version is present, we use create
                                 // We do this since there is no way to set an initial primary_term / seq_no combination
                                 // Or if using streams since this is the only supported operation
                                 ElasticVersion elasticVersion = entityHolder.getElasticVersionIfPresent();
                                 if((entityDescriptor.isOptimisticLockingEnabled()
                                         && elasticVersion == null)
                                     || entityDescriptor.isStream()
                                 ){

                                     return b.create(c ->
                                                             c.index(entityDescriptor.itemIndex())
                                                              .id(entityHolder.getDocumentId())
                                                              .routing(entityHolder.tenantId())
                                                              .document(entityHolder.entity()));
                                 }else{
                                     return b.index(i -> {
                                         i.index(entityDescriptor.itemIndex())
                                          .id(entityHolder.getDocumentId())
                                          .routing(entityHolder.tenantId())
                                          .document(entityHolder.entity());

                                         if(entityDescriptor.isOptimisticLockingEnabled()){
                                             i.ifPrimaryTerm(elasticVersion.primaryTerm());
                                             i.ifSeqNo(elasticVersion.seqNo());
                                         }
                                         return i;
                                     });
                                 }
                             }));
    }

    @WithSpan
    @Override
    public Future<Void> bulkUpdate(TokenBuffer entities, EntityContext context) {
        return doPersistBulk(entities,
                             EntityOperation.BULK_UPDATE,
                             context,
                             entityHolder -> BulkOperation.of(b -> b
                                     .update(u -> {

                                                 ElasticVersion elasticVersion = entityHolder.getElasticVersionIfPresent();
                                                 u.index(entityDescriptor.itemIndex())
                                                  .id(entityHolder.getDocumentId())
                                                  .routing(entityHolder.tenantId())
                                                  .action(upB -> {
                                                      upB.doc(entityHolder.entity())
                                                         .detectNoop(true);

                                                      if(elasticVersion == null){
                                                          upB.docAsUpsert(true);
                                                      }
                                                      return upB;
                                                  });

                                                 if(entityDescriptor.isOptimisticLockingEnabled()
                                                         && elasticVersion != null) {

                                                     u.ifPrimaryTerm(elasticVersion.primaryTerm())
                                                      .ifSeqNo(elasticVersion.seqNo());
                                                 } else if (entityDescriptor.isOptimisticLockingEnabled()) {
                                                     throw new IllegalArgumentException("A Version must be provided when calling update");
                                                 }
                                                 return u;
                                             }
                                     )));
    }

    @WithSpan
    @Override
    public Future<Long> count(EntityContext context) {
        return PersistenceUtil.validateEntityContext(entityDescriptor, context)
                .compose(un -> authService.authorize(EntityOperation.COUNT, context))
                .compose(un -> crudServiceTemplate
                        .count(entityDescriptor.itemIndex(),
                               builder -> readPreProcessor.beforeCount(entityDescriptor, null, builder, context)));
    }

    @WithSpan
    @Override
    public Future<Long> countByQuery(String query, EntityContext context) {
        return PersistenceUtil.validateEntityContext(entityDescriptor, context)
                .compose(un -> authService.authorize(EntityOperation.COUNT_BY_QUERY, context))
                .compose(un -> crudServiceTemplate
                        .count(entityDescriptor.itemIndex(),
                               builder -> readPreProcessor.beforeCount(entityDescriptor, query, builder, context)));
    }

    @WithSpan
    @Override
    public Future<Void> deleteById(String id, EntityContext context) {
        return PersistenceUtil.validateEntityContext(entityDescriptor, context)
                .compose(un -> authService.authorize(EntityOperation.DELETE_BY_ID, context))
                .map(un -> composeId(id, context))
                .compose(composedId -> crudServiceTemplate
                        .deleteById(entityDescriptor.itemIndex(),
                                    composedId,
                                    builder -> readPreProcessor.beforeDelete(entityDescriptor, builder, context))
                        .mapEmpty());
    }

    @WithSpan
    @Override
    public Future<Void> deleteById(TenantSpecificId id, EntityContext context) {
        // We set the tenant selection so validation below will know that a tenant specific operation is being used
        context.setTenantSelection(List.of(id.tenantId()));

        return PersistenceUtil.validateEntityContext(entityDescriptor, context)
                .compose(un -> authService.authorize(EntityOperation.DELETE_BY_ID, context))
                .map(un -> composeId(id))
                .compose(composedId -> crudServiceTemplate
                        .deleteById(entityDescriptor.itemIndex(),
                                    composedId,
                                    builder -> readPreProcessor.beforeDelete(entityDescriptor, builder, context))
                        .mapEmpty());
    }

    @WithSpan
    @Override
    public Future<Void> deleteByQuery(String query, EntityContext context) {
        return PersistenceUtil.validateEntityContext(entityDescriptor, context)
                .compose(un -> authService.authorize(EntityOperation.DELETE_BY_QUERY, context))
                .compose(un -> crudServiceTemplate
                        .deleteByQuery(entityDescriptor.itemIndex(),
                                       builder -> readPreProcessor.beforeDeleteByQuery(entityDescriptor, query, builder, context))
                        .mapEmpty());
    }

    @WithSpan
    @Override
    public <T> Future<Page<T>> findAll(Pageable pageable, Class<T> type, EntityContext context) {
        return PersistenceUtil.validateEntityContext(entityDescriptor, context)
                .compose(un -> authService.authorize(EntityOperation.FIND_ALL, context))
                .compose(un -> crudServiceTemplate
                        .search(entityDescriptor.itemIndex(),
                                pageable,
                                sourceType(type),
                                builder -> readPreProcessor.beforeFindAll(entityDescriptor, builder, context),
                                hit -> toResult(hit.source(), hit.primaryTerm(), hit.seqNo(), type)))
                .map(readPostProcessor.paranoidCheck(entityDescriptor, context, "FindAll"));
    }

    @WithSpan
    @Override
    public <T> Future<T> findById(String id, Class<T> type, EntityContext context) {
        return PersistenceUtil.validateEntityContext(entityDescriptor, context)
                .compose(un -> authService.authorize(EntityOperation.FIND_BY_ID, context))
                .map(un -> composeId(id, context))
                .compose(composedId -> doFindById(composedId, type, context));
    }

    @WithSpan
    @Override
    public <T> Future<T> findById(TenantSpecificId id, Class<T> type, EntityContext context) {
        // We set the tenant selection so validation below will know that a tenant specific operation is being used
        context.setTenantSelection(List.of(id.tenantId()));

        return PersistenceUtil.validateEntityContext(entityDescriptor, context)
                .compose(un -> authService.authorize(EntityOperation.FIND_BY_ID, context))
                .map(un -> composeId(id))
                .compose(composedId -> doFindById(composedId, type, context));
    }

    @WithSpan
    @Override
    public <T> Future<List<T>> findByIds(List<String> ids, Class<T> type, EntityContext context) {
        return PersistenceUtil.validateEntityContext(entityDescriptor, context)
                .compose(un -> authService.authorize(EntityOperation.FIND_BY_IDS, context))
                .map(un -> composeIds(ids, context))
                .compose(composedIds -> doFindByIds(composedIds, type, context));
    }

    @WithSpan
    @Override
    public <T> Future<List<T>> findByIdsWithTenant(List<TenantSpecificId> ids, Class<T> type, EntityContext context) {
        return  validate_ComposeIds_AddTenantsToContext(ids, context)
                .compose(composedIds
                                     -> authService.authorize(EntityOperation.FIND_BY_IDS, context)
                                                   .compose(v -> doFindByIds(composedIds, type, context)));
    }

    @WithSpan
    @Override
    public <T> Future<List<T>> namedQuery(String queryName,
                                          ParameterHolder parameterHolder,
                                          Class<T> type,
                                          EntityContext context) {
        // Validation and authorization happen in the QueryExecutor, after the query's parameters have set any tenant selection
        return namedQueriesService.executeNamedQuery(entityDescriptor,
                                                     queryName,
                                                     parameterHolder,
                                                     type,
                                                     context);
    }

    @WithSpan
    @Override
    public <T> Future<Page<T>> namedQueryPage(String queryName,
                                              ParameterHolder parameterHolder,
                                              Pageable pageable,
                                              Class<T> type,
                                              EntityContext context) {
        // Validation and authorization happen in the QueryExecutor, after the query's parameters have set any tenant selection
        return namedQueriesService.executeNamedQueryPage(entityDescriptor,
                                                         queryName,
                                                         parameterHolder,
                                                         pageable,
                                                         type,
                                                         context);
    }

    @WithSpan
    @Override
    public Future<RawJson> save(TokenBuffer entity, EntityContext context) {
        return doPersist(entity,
                         EntityOperation.SAVE,
                         context,
                         entityHolder -> KinoticUtil.toFuture(esAsyncClient.index(i -> {
                             i.routing(entityHolder.tenantId())
                              .index(entityDescriptor.itemIndex())
                              .id(entityHolder.getDocumentId())
                              .document(entityHolder.entity())
                              .refresh(Refresh.True);

                             // When optimistic locking is enabled and no version is present we use create
                             // We do this since there is no way to set an initial primary_term / seq_no combination
                             // Or if using streams since this is the only supported operation
                             ElasticVersion elasticVersion = entityHolder.getElasticVersionIfPresent();
                             if((entityDescriptor.isOptimisticLockingEnabled()
                                     && elasticVersion == null)
                                 || entityDescriptor.isStream()
                             ){

                                 i.opType(OpType.Create);

                             }else if(entityDescriptor.isOptimisticLockingEnabled()){

                                 i.ifPrimaryTerm(elasticVersion.primaryTerm());
                                 i.ifSeqNo(elasticVersion.seqNo());
                             }

                             return i;
                         })).map(indexResponse -> versionWriter.write(entityHolder.entity(),
                                                                      indexResponse.primaryTerm(),
                                                                      indexResponse.seqNo())));
    }

    @WithSpan
    @Override
    public <T> Future<Page<T>> search(String searchText, Pageable pageable, Class<T> type, EntityContext context) {
        return PersistenceUtil.validateEntityContext(entityDescriptor, context)
                .compose(un -> authService.authorize(EntityOperation.SEARCH, context))
                .compose(un -> crudServiceTemplate
                        .search(entityDescriptor.itemIndex(),
                                pageable,
                                sourceType(type),
                                builder -> readPreProcessor.beforeSearch(entityDescriptor, searchText, builder, context),
                                hit -> toResult(hit.source(), hit.primaryTerm(), hit.seqNo(), type)))
                .map(readPostProcessor.paranoidCheck(entityDescriptor, context, "Search"));
    }

    @WithSpan
    @Override
    public Future<Void> syncIndex(EntityContext context) {
        return authService.authorize(EntityOperation.SYNC_INDEX, context)
                          .compose(un -> crudServiceTemplate.syncIndex(entityDescriptor.itemIndex()));
    }

    @WithSpan
    @Override
    public Future<RawJson> update(TokenBuffer entity, EntityContext context) {
        return doPersist(entity,
                         EntityOperation.UPDATE,
                         context,
                         entityHolder -> {

                             UpdateRequest<?,?> request = UpdateRequest.of(u -> {
                                 u.routing(entityHolder.tenantId())
                                  .index(entityDescriptor.itemIndex())
                                  .id(entityHolder.getDocumentId())
                                  .doc(entityHolder.entity())
                                  .refresh(Refresh.True);

                                 ElasticVersion elasticVersion = entityHolder.getElasticVersionIfPresent();
                                 if(entityDescriptor.isOptimisticLockingEnabled()
                                         && elasticVersion != null) {

                                     u.ifPrimaryTerm(elasticVersion.primaryTerm())
                                      .ifSeqNo(elasticVersion.seqNo());

                                 } else if (entityDescriptor.isOptimisticLockingEnabled()) {
                                     throw new IllegalArgumentException("A Version must be provided when calling update");
                                 }else{
                                     u.docAsUpsert(true);
                                 }
                                 return u;
                             });

                             return KinoticUtil.toFuture(esAsyncClient.update(request, entityHolder.entity().getClass()))
                                                       .map(updateResponse ->
                                                                    versionWriter.write(entityHolder.entity(),
                                                                                        updateResponse.primaryTerm(),
                                                                                            updateResponse.seqNo()));
                         });
    }

    private String composeId(final String id, final EntityContext context){
        String ret;
        if(entityDescriptor.multiTenancyType() == MultiTenancyType.SHARED){
            ret = DomainUtil.createEntityDocumentId(MultiTenancyType.SHARED, context.requireTenantId(), id);
        }else{
            ret = id;
        }
        return ret;
    }

    private String composeId(final TenantSpecificId id){
        return DomainUtil.createEntityDocumentId(MultiTenancyType.SHARED, id.tenantId(), id.entityId());
    }

    private List<MultiGetOperation> composeIds(final List<String> ids, final EntityContext context){
        List<MultiGetOperation> ret = new ArrayList<>(ids.size());
        boolean multiTenancyShared = entityDescriptor.multiTenancyType() == MultiTenancyType.SHARED;

        String tenantId = multiTenancyShared ? context.requireTenantId() : null;
        for (String id : ids){
            MultiGetOperation.Builder builder =  new MultiGetOperation.Builder();
            builder.index(entityDescriptor.itemIndex());
            if(multiTenancyShared){
                builder.id(DomainUtil.createEntityDocumentId(MultiTenancyType.SHARED, tenantId, id))
                       .routing(tenantId);
            }else{
                builder.id(id);
            }
            ret.add(builder.build());
        }
        return ret;
    }

    private <T> Future<T> doFindById(String id, Class<T> type, EntityContext context) {
        return crudServiceTemplate
                .findById(entityDescriptor.itemIndex(),
                          id,
                          sourceType(type),
                          builder -> readPreProcessor.beforeFindById(entityDescriptor, builder, context),
                          result -> toResult(result.source(), result.primaryTerm(), result.seqNo(), type));
    }

    private <T> Future<List<T>> doFindByIds(List<MultiGetOperation> composedIds,
                                            Class<T> type,
                                            EntityContext context) {
        return crudServiceTemplate
                .multiGet(composedIds,
                          sourceType(type),
                          builder -> readPreProcessor.beforeFindByIds(entityDescriptor, builder, context),
                          result -> toResult(result.source(), result.primaryTerm(), result.seqNo(), type));
    }

    // A FastestType is read as whichever type goes back out to the caller with the least work: RawJson, which is
    // written out as it is, or for an entity with a version field a Map, which takes the version with a put
    private Class<?> sourceType(Class<?> type) {
        Class<?> ret;
        if(!FastestType.class.isAssignableFrom(type)){
            ret = type;
        }else if(entityDescriptor.isOptimisticLockingEnabled()){
            ret = Map.class;
        }else{
            ret = RawJson.class;
        }
        return ret;
    }

    private <T> T toResult(Object source, Long primaryTerm, Long seqNo, Class<T> type) {
        Object entity = entityDescriptor.isOptimisticLockingEnabled() ? withVersion(source, primaryTerm, seqNo) : source;
        return type.cast(FastestType.class.isAssignableFrom(type) ? new FastestType(entity) : entity);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object withVersion(Object source, Long primaryTerm, Long seqNo) {
        String version = primaryTerm + ":" + seqNo;
        Object ret;
        switch (source) {
            case Map map -> {
                map.put(entityDescriptor.versionFieldName(), version);
                ret = map;
            }
            case RawJson rawJson -> {
                // bound to a tree so that a value stored under the version field is replaced
                ObjectNode node = (ObjectNode) objectMapper.readTree(rawJson.data());
                node.put(entityDescriptor.versionFieldName(), version);
                ret = new RawJson(objectMapper.writeValueAsBytes(node));
            }
            case null, default -> throw new IllegalArgumentException("Pojo Not Supported for Version");
        }
        return ret;
    }

    private Future<RawJson> doPersist(TokenBuffer entity,
                                      EntityOperation operation,
                                      EntityContext context,
                                      Function<EntityHolder<RawJson>, Future<RawJson>> persistLogic){
        // We do this since ideally processing data before auth is not ideal
        // However, in the case of Multi-tenant access the data names the tenants, so they must be
        // extracted before the context is validated and authorized
        if(entityDescriptor.isMultiTenantSelectionEnabled()){

            return upsertPreProcessor.process(entity, context)
                    .compose(entityHolder ->
                                         PersistenceUtil.validateEntityContext(entityDescriptor, context)
                                                 .compose(un -> authService.authorize(operation, context))
                                                 .compose(un -> persistLogic.apply(entityHolder)));
        }else{
            return PersistenceUtil.validateEntityContext(entityDescriptor, context)
                    .compose(un -> authService.authorize(operation, context))
                    .compose(un -> upsertPreProcessor.process(entity, context))
                    .compose(persistLogic);
        }
    }

    private Future<Void> doPersistBulk(TokenBuffer entities,
                                       EntityOperation operation,
                                       EntityContext context,
                                       Function<EntityHolder<RawJson>, BulkOperation> persistLogic){
        // We do this since ideally processing data before auth is not ideal
        // However, in the case of Multi-tenant access the data names the tenants, so they must be
        // extracted before the context is validated and authorized
        if(entityDescriptor.isMultiTenantSelectionEnabled()){

            return upsertPreProcessor.processArray(entities, context)
                    .compose(entityList ->
                                         PersistenceUtil.validateEntityContext(entityDescriptor, context)
                                                 .compose(un -> authService.authorize(operation, context))
                                                 .compose(un -> doPersistBulkLogic(entityList, persistLogic)))
                    .mapEmpty();
        }else {
            return PersistenceUtil.validateEntityContext(entityDescriptor, context)
                    .compose(un -> authService.authorize(operation, context))
                    .compose(un -> upsertPreProcessor.processArray(entities, context))
                    .compose(list -> doPersistBulkLogic(list, persistLogic))
                    .mapEmpty();
        }
    }

    private Future<BulkResponse> doPersistBulkLogic(List<EntityHolder<RawJson>> list,
                                                    Function<EntityHolder<RawJson>, BulkOperation> persistLogic) {

        BulkRequest.Builder br = new BulkRequest.Builder();

        List<BulkOperation> bulkOperations = new ArrayList<>(list.size());
        for(EntityHolder<RawJson> entityHolder : list){
            bulkOperations.add(persistLogic.apply(entityHolder));
        }

        if(bulkOperations.isEmpty()){
            return Future.failedFuture(new IllegalArgumentException("No items found to create bulk request for"));
        }

        br.operations(bulkOperations);

        return KinoticUtil.toFuture(esAsyncClient.bulk(br.build())).compose(bulkResponse -> {
            if (bulkResponse.errors()) {
                StringBuilder builder = new StringBuilder();
                for (BulkResponseItem item : bulkResponse.items()) {
                    var error = item.error();
                    if (error != null && error.reason() != null && builder.indexOf(error.reason()) == -1) {
                        builder.append(error.reason()).append("\n");
                    }
                }
                String errorMessage = !builder.isEmpty() ? builder.toString() : "Unknown error occurred during bulk operation";
                return Future.failedFuture(new IllegalArgumentException("Bulk save failed with errors:\n" + errorMessage));
            } else {
                return Future.succeededFuture(bulkResponse);
            }
        });
    }

    private Future<List<MultiGetOperation>> validate_ComposeIds_AddTenantsToContext(final List<TenantSpecificId> ids, EntityContext entityContext){
        List<MultiGetOperation> ret = new ArrayList<>(ids.size());
        Set<String> tenants = new LinkedHashSet<>();
        for (TenantSpecificId id : ids) {
            MultiGetOperation.Builder builder = new MultiGetOperation.Builder();
            builder.index(entityDescriptor.itemIndex())
                   .id(composeId(id))
                   .routing(id.tenantId());
            ret.add(builder.build());
            tenants.add(id.tenantId());
        }
        entityContext.setTenantSelection(new ArrayList<>(tenants));
        return PersistenceUtil.validateEntityContext(entityDescriptor, entityContext).map(ret);
    }

}
