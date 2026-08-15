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
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.ObjectUtils;
import org.kinotic.domain.api.model.RawJson;
import org.kinotic.core.api.crud.CursorPage;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.persistence.api.config.PersistenceProperties;
import org.kinotic.persistence.api.model.*;
import org.kinotic.persistence.api.model.idl.decorators.MultiTenancyType;
import org.kinotic.persistence.api.services.NamedQueriesService;
import org.kinotic.persistence.api.services.security.AuthorizationService;
import org.kinotic.persistence.internal.api.hooks.DelegatingUpsertPreProcessor;
import org.kinotic.persistence.internal.api.hooks.ReadPreProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonPointer;
import tools.jackson.databind.JsonNode;
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

    private static final Logger log = LoggerFactory.getLogger(DefaultEntityService.class);

    private final AuthorizationService<EntityOperation> authService;
    private final CrudServiceTemplate crudServiceTemplate;
    private final DelegatingUpsertPreProcessor delegatingUpsertPreProcessor;
    private final ElasticsearchAsyncClient esAsyncClient;
    private final NamedQueriesService namedQueriesService;
    private final ObjectMapper objectMapper;
    private final ReadPreProcessor readPreProcessor;
    private final EntityDefinition entityDefinition;
    private final PersistenceProperties persistenceProperties;

    @WithSpan
    @Override
    public <T> Future<Void> bulkSave(T entities, EntityContext context) {
        return doPersistBulk(entities,
                             EntityOperation.BULK_SAVE,
                             context,
                             entityHolder -> BulkOperation.of(b -> {
                                 // When optimistic locking is enabled and no version is present, we use create
                                 // We do this since there is no way to set an initial primary_term / seq_no combination
                                 // Or if using streams since this is the only supported operation
                                 ElasticVersion elasticVersion = entityHolder.getElasticVersionIfPresent();
                                 if((entityDefinition.isOptimisticLockingEnabled()
                                         && elasticVersion == null)
                                     || entityDefinition.isStream()
                                 ){

                                     return b.create(c ->
                                                             c.index(entityDefinition.getItemIndex())
                                                              .id(entityHolder.getDocumentId())
                                                              .routing(entityHolder.tenantId())
                                                              .document(entityHolder.entity()));
                                 }else{
                                     return b.index(i -> {
                                         i.index(entityDefinition.getItemIndex())
                                          .id(entityHolder.getDocumentId())
                                          .routing(entityHolder.tenantId())
                                          .document(entityHolder.entity());

                                         if(entityDefinition.isOptimisticLockingEnabled()
                                                 && elasticVersion != null){
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
    public <T> Future<Void> bulkUpdate(T entities, EntityContext context) {
        return doPersistBulk(entities,
                             EntityOperation.BULK_UPDATE,
                             context,
                             entityHolder -> BulkOperation.of(b -> b
                                     .update(u -> {

                                                 ElasticVersion elasticVersion = entityHolder.getElasticVersionIfPresent();
                                                 u.index(entityDefinition.getItemIndex())
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

                                                 if(entityDefinition.isOptimisticLockingEnabled()
                                                         && elasticVersion != null) {

                                                     u.ifPrimaryTerm(elasticVersion.primaryTerm())
                                                      .ifSeqNo(elasticVersion.seqNo());
                                                 } else if (entityDefinition.isOptimisticLockingEnabled()) {
                                                     throw new IllegalArgumentException("A Version must be provided when calling update");
                                                 }
                                                 return u;
                                             }
                                     )));
    }

    @WithSpan
    @Override
    public Future<Long> count(EntityContext context) {
        return validateContext(context)
                .compose(un -> authService.authorize(EntityOperation.COUNT, context))
                .compose(un -> crudServiceTemplate
                        .count(entityDefinition.getItemIndex(),
                               builder -> readPreProcessor.beforeCount(entityDefinition, null, builder, context)));
    }

    @WithSpan
    @Override
    public Future<Long> countByQuery(String query, EntityContext context) {
        return validateContext(context)
                .compose(un -> authService.authorize(EntityOperation.COUNT_BY_QUERY, context))
                .compose(un -> crudServiceTemplate
                        .count(entityDefinition.getItemIndex(),
                               builder -> readPreProcessor.beforeCount(entityDefinition, query, builder, context)));
    }

    @WithSpan
    @Override
    public Future<Void> deleteById(String id, EntityContext context) {
        return validateContext(context)
                .compose(un -> authService.authorize(EntityOperation.DELETE_BY_ID, context))
                .map(un -> composeId(id, context))
                .compose(composedId -> crudServiceTemplate
                        .deleteById(entityDefinition.getItemIndex(),
                                    composedId,
                                    builder -> readPreProcessor.beforeDelete(entityDefinition, builder, context))
                        .mapEmpty());
    }

    @WithSpan
    @Override
    public Future<Void> deleteById(TenantSpecificId id, EntityContext context) {
        // We set the tenant selection so validation below will know that a tenant specific operation is being used
        context.setTenantSelection(List.of(id.tenantId()));

        return validateContext(context)
                .compose(un -> authService.authorize(EntityOperation.DELETE_BY_ID, context))
                .map(un -> composeId(id))
                .compose(composedId -> crudServiceTemplate
                        .deleteById(entityDefinition.getItemIndex(),
                                    composedId,
                                    builder -> readPreProcessor.beforeDelete(entityDefinition, builder, context))
                        .mapEmpty());
    }

    @WithSpan
    @Override
    public Future<Void> deleteByQuery(String query, EntityContext context) {
        return validateContext(context)
                .compose(un -> authService.authorize(EntityOperation.DELETE_BY_QUERY, context))
                .compose(un -> crudServiceTemplate
                        .deleteByQuery(entityDefinition.getItemIndex(),
                                       builder -> readPreProcessor.beforeDeleteByQuery(entityDefinition, query, builder, context))
                        .mapEmpty());
    }

    @WithSpan
    @Override
    public <T> Future<Page<T>> findAll(Pageable pageable, Class<T> type, EntityContext context) {
        return validateContext(context)
                .compose(un -> authService.authorize(EntityOperation.FIND_ALL, context))
                .compose(un -> {

                    if(FastestType.class.isAssignableFrom(type)){

                        if(entityDefinition.isOptimisticLockingEnabled()){
                            return crudServiceTemplate
                                    .search(entityDefinition.getItemIndex(),
                                            pageable,
                                            Map.class,
                                            builder -> readPreProcessor.beforeFindAll(entityDefinition, builder, context),
                                            hit -> type.cast(new FastestType(updateVersionForEntity(hit.source(),
                                                                                              hit.primaryTerm(),
                                                                                              hit.seqNo()
                                            ))));
                        }else{
                            return crudServiceTemplate
                                    .search(entityDefinition.getItemIndex(),
                                            pageable,
                                            RawJson.class,
                                            builder -> readPreProcessor.beforeFindAll(entityDefinition, builder, context),
                                            hit -> type.cast(new FastestType(hit.source())));
                        }
                    }else{

                        if(entityDefinition.isOptimisticLockingEnabled()){
                            return crudServiceTemplate
                                    .search(entityDefinition.getItemIndex(),
                                            pageable,
                                            type,
                                            builder -> readPreProcessor.beforeFindAll(entityDefinition, builder, context),
                                            hit -> updateVersionForEntity(hit.source(),
                                                                          hit.primaryTerm(),
                                                                          hit.seqNo()
                                            ));
                        }else{
                            return crudServiceTemplate
                                    .search(entityDefinition.getItemIndex(),
                                            pageable,
                                            type,
                                            builder -> readPreProcessor.beforeFindAll(entityDefinition, builder, context));
                        }
                    }
                }).map(createParanoidCheck(context, "FindAll"));
    }

    @WithSpan
    @Override
    public <T> Future<T> findById(String id, Class<T> type, EntityContext context) {
        return validateContext(context)
                .compose(un -> authService.authorize(EntityOperation.FIND_BY_ID, context))
                .map(un -> composeId(id, context))
                .compose(composedId -> doFindById(composedId, type, context));
    }

    @WithSpan
    @Override
    public <T> Future<T> findById(TenantSpecificId id, Class<T> type, EntityContext context) {
        // We set the tenant selection so validation below will know that a tenant specific operation is being used
        context.setTenantSelection(List.of(id.tenantId()));

        return validateContext(context)
                .compose(un -> authService.authorize(EntityOperation.FIND_BY_ID, context))
                .map(un -> composeId(id))
                .compose(composedId -> doFindById(composedId, type, context));
    }

    @WithSpan
    @Override
    public <T> Future<List<T>> findByIds(List<String> ids, Class<T> type, EntityContext context) {
        return validateContext(context)
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
        // Authorization happens in the QueryExecutor so we don't need an additional cache to hold the NamedQueryAuthorizationService
        return validateContext(context)
                .compose(unused -> namedQueriesService.executeNamedQuery(entityDefinition,
                                                                         queryName,
                                                                         parameterHolder,
                                                                         type,
                                                                         context));
    }

    @WithSpan
    @Override
    public <T> Future<Page<T>> namedQueryPage(String queryName,
                                              ParameterHolder parameterHolder,
                                              Pageable pageable,
                                              Class<T> type,
                                              EntityContext context) {
        // Authorization happens in the QueryExecutor so we don't need an additional cache to hold the NamedQueryAuthorizationService
        return validateContext(context)
                .compose(unused -> namedQueriesService.executeNamedQueryPage(entityDefinition,
                                                                             queryName,
                                                                             parameterHolder,
                                                                             pageable,
                                                                             type,
                                                                             context));
    }

    @WithSpan
    @Override
    public <T> Future<T> save(T entity, EntityContext context) {
        return doPersist(entity,
                         EntityOperation.SAVE,
                         context,
                         entityHolder -> crudServiceTemplate.toFuture(esAsyncClient.index(i -> {
                             i.routing(entityHolder.tenantId())
                              .index(entityDefinition.getItemIndex())
                              .id(entityHolder.getDocumentId())
                              .document(entityHolder.entity())
                              .refresh(Refresh.True);

                             // When optimistic locking is enabled and no version is present we use create
                             // We do this since there is no way to set an initial primary_term / seq_no combination
                             // Or if using streams since this is the only supported operation
                             ElasticVersion elasticVersion = entityHolder.getElasticVersionIfPresent();
                             if((entityDefinition.isOptimisticLockingEnabled()
                                     && elasticVersion == null)
                                 || entityDefinition.isStream()
                             ){

                                 i.opType(OpType.Create);

                             }else if(entityDefinition.isOptimisticLockingEnabled()
                                     && elasticVersion != null){

                                 i.ifPrimaryTerm(elasticVersion.primaryTerm());
                                 i.ifSeqNo(elasticVersion.seqNo());
                             }

                             return i;
                         })).map(indexResponse -> postProcessSaveOrUpdate(entity,
                                                                          entityHolder,
                                                                          indexResponse.primaryTerm(),
                                                                          indexResponse.seqNo())));
    }

    @WithSpan
    @Override
    public <T> Future<Page<T>> search(String searchText, Pageable pageable, Class<T> type, EntityContext context) {
        return validateContext(context)
                .compose(un -> authService.authorize(EntityOperation.SEARCH, context))
                .compose(un -> {

                    if(FastestType.class.isAssignableFrom(type)){

                        if(entityDefinition.isOptimisticLockingEnabled()){
                            return crudServiceTemplate
                                    .search(entityDefinition.getItemIndex(),
                                            pageable,
                                            Map.class,
                                            builder -> readPreProcessor.beforeSearch(entityDefinition,
                                                                                     searchText,
                                                                                     builder,
                                                                                     context),
                                            hit -> type.cast(new FastestType(updateVersionForEntity(hit.source(),
                                                                                              hit.primaryTerm(),
                                                                                              hit.seqNo()
                                            ))));
                        }else{
                            return crudServiceTemplate
                                    .search(entityDefinition.getItemIndex(),
                                            pageable,
                                            RawJson.class,
                                            builder -> readPreProcessor.beforeSearch(entityDefinition,
                                                                                     searchText,
                                                                                     builder,
                                                                                     context),
                                            hit -> type.cast(new FastestType(hit.source())));
                        }
                    }else{

                        if(entityDefinition.isOptimisticLockingEnabled()){
                            return crudServiceTemplate
                                    .search(entityDefinition.getItemIndex(),
                                            pageable,
                                            type,
                                            builder -> readPreProcessor.beforeSearch(entityDefinition,
                                                                                     searchText,
                                                                                     builder,
                                                                                     context),
                                            hit -> updateVersionForEntity(hit.source(),
                                                                          hit.primaryTerm(),
                                                                          hit.seqNo()
                                            ));
                        }else{
                            return crudServiceTemplate
                                    .search(entityDefinition.getItemIndex(),
                                            pageable,
                                            type,
                                            builder -> readPreProcessor.beforeSearch(entityDefinition,
                                                                                     searchText,
                                                                                     builder,
                                                                                     context));
                        }
                    }
                }).map(createParanoidCheck(context, "Search"));
    }

    @WithSpan
    @Override
    public Future<Void> syncIndex(EntityContext context) {
        return authService.authorize(EntityOperation.SYNC_INDEX, context)
                          .compose(un -> crudServiceTemplate.syncIndex(entityDefinition.getItemIndex()));
    }

    @WithSpan
    @Override
    public <T> Future<T> update(T entity, EntityContext context) {
        return doPersist(entity,
                         EntityOperation.UPDATE,
                         context,
                         entityHolder -> {

                             UpdateRequest<?,?> request = UpdateRequest.of(u -> {
                                 u.routing(entityHolder.tenantId())
                                  .index(entityDefinition.getItemIndex())
                                  .id(entityHolder.getDocumentId())
                                  .doc(entityHolder.entity())
                                  .refresh(Refresh.True);

                                 ElasticVersion elasticVersion = entityHolder.getElasticVersionIfPresent();
                                 if(entityDefinition.isOptimisticLockingEnabled()
                                         && elasticVersion != null) {

                                     u.ifPrimaryTerm(elasticVersion.primaryTerm())
                                      .ifSeqNo(elasticVersion.seqNo());

                                 } else if (entityDefinition.isOptimisticLockingEnabled()) {
                                     throw new IllegalArgumentException("A Version must be provided when calling update");
                                 }else{
                                     u.docAsUpsert(true);
                                 }
                                 return u;
                             });

                             return crudServiceTemplate.toFuture(esAsyncClient.update(request, entityHolder.entity().getClass()))
                                                       .map(updateResponse ->
                                                                    postProcessSaveOrUpdate(entity,
                                                                                            entityHolder,
                                                                                            updateResponse.primaryTerm(),
                                                                                            updateResponse.seqNo()));
                         });
    }

    private String composeId(final String id, final EntityContext context){
        String ret;
        if(entityDefinition.getMultiTenancyType() == MultiTenancyType.SHARED){
            String tenantId = context.getParticipant().getTenantId();
            ret = tenantId + "-" + id;
        }else{
            ret = id;
        }
        return ret;
    }

    private String composeId(final TenantSpecificId id){
        return id.tenantId() + "-" + id.entityId();
    }

    private List<MultiGetOperation> composeIds(final List<String> ids, final EntityContext context){
        List<MultiGetOperation> ret = new ArrayList<>(ids.size());
        boolean multiTenancyShared = entityDefinition.getMultiTenancyType() == MultiTenancyType.SHARED;

        String tenantId = context.getParticipant().getTenantId();
        for (String id : ids){
            MultiGetOperation.Builder builder =  new MultiGetOperation.Builder();
            builder.index(entityDefinition.getItemIndex());
            if(multiTenancyShared){
                builder.id(tenantId + "-" + id)
                       .routing(tenantId);
            }else{
                builder.id(id);
            }
            ret.add(builder.build());
        }
        return ret;
    }

    @WithSpan
    private <T> Function<Page<T>, Page<T>> createParanoidCheck(EntityContext context, String what){
        return page -> {
            // This is a temporary bit of code to make sure multi tenancy is working properly
            if(entityDefinition.getMultiTenancyType() == MultiTenancyType.SHARED){
                String tenantIdFieldName
                        = entityDefinition.isMultiTenantSelectionEnabled()
                        ? entityDefinition.getTenantIdFieldName() : persistenceProperties.getTenantIdFieldName();

                List<Object> result = new ArrayList<>(page.getContent().size());
                Set<String> tenantIds = Collections.emptySet();
                if(context.hasTenantSelection()) {
                    tenantIds = new HashSet<>(context.getTenantSelection());
                }

                for(Object object : page.getContent()){
                    String tenant = extractTenant(object, tenantIdFieldName);
                    // Find and search methods will use the logged in tenant if no multi tenant selection is provided
                    if(context.hasTenantSelection()){
                        if(tenant != null && tenantIds.contains(tenant)){
                            result.add(object);
                        }else{
                            log.error(
                                    "{} Multi tenancy is not working properly for EntityDefinition: {} and expected one of: {} got: {}\nData:\n{}",
                                    what,
                                    entityDefinition,
                                    String.join(",", tenantIds),
                                    tenant,
                                    formatToPrintJson(object));
                        }
                    }else {
                        if (tenant != null && tenant.equals(context.getParticipant().getTenantId())) {
                            result.add(object);
                        }else{
                            log.error(
                                    "{} Multi tenancy is not working properly for EntityDefinition: {} and expected tenant: {} got: {}\nData:\n{}",
                                    what,
                                    entityDefinition,
                                    context.getParticipant().getTenantId(),
                                    tenant,
                                    formatToPrintJson(object));
                        }
                    }
                }

                if(page instanceof CursorPage){
                    @SuppressWarnings("unchecked")
                    Page<T> newPage = (Page<T>) new CursorPage<>(result, ((CursorPage<?>) page).getCursor(), page.getTotalElements());
                    return newPage;
                }else{
                    @SuppressWarnings("unchecked")
                    Page<T> newPage = (Page<T>) new Page<>(result, page.getTotalElements());
                    return newPage;
                }
            }else{
                return page;
            }
        };
    }

    private <T> Future<T> doFindById(String id, Class<T> type, EntityContext context) {
        if(FastestType.class.isAssignableFrom(type)){
            if(entityDefinition.isOptimisticLockingEnabled()){
                return crudServiceTemplate
                        .findById(entityDefinition.getItemIndex(),
                                  id,
                                  Map.class,
                                  builder -> readPreProcessor.beforeFindById(entityDefinition, builder, context),
                                  result -> type.cast(new FastestType(updateVersionForEntity(result.source(),
                                                                                     result.primaryTerm(),
                                                                                     result.seqNo()
                                  ))));
            }else{
                return crudServiceTemplate
                        .findById(entityDefinition.getItemIndex(),
                                  id,
                                  RawJson.class,
                                  builder -> readPreProcessor.beforeFindById(entityDefinition, builder, context),
                                  result -> type.cast(new FastestType(result.source())));
            }
        }else{

            if(entityDefinition.isOptimisticLockingEnabled()){
                return crudServiceTemplate
                        .findById(entityDefinition.getItemIndex(),
                                  id,
                                  type,
                                  builder -> readPreProcessor.beforeFindById(entityDefinition, builder, context),
                                  result -> updateVersionForEntity(result.source(),
                                                                   result.primaryTerm(),
                                                                   result.seqNo()
                                  ));
            }else{
                return crudServiceTemplate
                        .findById(entityDefinition.getItemIndex(),
                                  id,
                                  type,
                                  builder -> readPreProcessor.beforeFindById(entityDefinition, builder, context));
            }
        }
    }

    private <T> Future<List<T>> doFindByIds(List<MultiGetOperation> composedIds,
                                            Class<T> type,
                                            EntityContext context) {
        if(FastestType.class.isAssignableFrom(type)){
            if(entityDefinition.isOptimisticLockingEnabled()){
                return crudServiceTemplate
                        .multiGet(composedIds,
                                  Map.class,
                                  builder -> readPreProcessor.beforeFindByIds(entityDefinition, builder, context),
                                  result -> type.cast(new FastestType(updateVersionForEntity(result.source(),
                                                                                     result.primaryTerm(),
                                                                                     result.seqNo()
                                  ))));
            }else{
                return crudServiceTemplate
                        .multiGet(composedIds,
                                  RawJson.class,
                                  builder -> readPreProcessor.beforeFindByIds(entityDefinition, builder, context),
                                  result -> type.cast(new FastestType(result.source())));
            }
        }else{

            if(entityDefinition.isOptimisticLockingEnabled()){
                return crudServiceTemplate
                        .multiGet(composedIds,
                                  type,
                                  builder -> readPreProcessor.beforeFindByIds(entityDefinition, builder, context),
                                  result -> updateVersionForEntity(result.source(),
                                                                   result.primaryTerm(),
                                                                   result.seqNo()
                                  ));
            }else{
                return crudServiceTemplate
                        .multiGet(composedIds,
                                  type,
                                  builder -> readPreProcessor.beforeFindByIds(entityDefinition, builder, context),
                                  null);
            }
        }
    }

    private <T> Future<T> doPersist(T entity,
                                    EntityOperation operation,
                                    EntityContext context,
                                    Function<EntityHolder<?>, Future<T>> persistLogic){
        // We do this since ideally processing data before auth is not ideal
        // However, in the case of Multi-tenant access we must extract tenant ids prior to calling auth
        if(entityDefinition.isMultiTenantSelectionEnabled()){

            return validateContext(context)
                    .compose(un -> delegatingUpsertPreProcessor.process(entity, context))
                    .compose(entityHolder ->
                                         authService.authorize(operation, context)
                                                    .compose(un -> persistLogic.apply(entityHolder)));
        }else{
            return validateContext(context)
                    .compose(un -> authService.authorize(operation, context))
                    .compose(un -> delegatingUpsertPreProcessor.process(entity, context))
                    .compose(persistLogic);
        }
    }

    private <T> Future<Void> doPersistBulk(T entities,
                                           EntityOperation operation,
                                           EntityContext context,
                                           Function<EntityHolder<?>, BulkOperation> persistLogic){
        // We do this since ideally processing data before auth is not ideal
        // However, in the case of Multi-tenant access we must extract tenant ids prior to calling auth
        if(entityDefinition.isMultiTenantSelectionEnabled()){

            return validateContext(context)
                    .compose(un -> delegatingUpsertPreProcessor.processArray(entities, context))
                    .compose(entityList ->
                                         authService.authorize(operation, context)
                                                    .compose(un -> doPersistBulkLogic(entityList, persistLogic)))
                    .mapEmpty();
        }else {
            return validateContext(context)
                    .compose(un -> authService.authorize(operation, context))
                    .compose(un -> delegatingUpsertPreProcessor.processArray(entities, context))
                    .compose(list -> doPersistBulkLogic(list, persistLogic))
                    .mapEmpty();
        }
    }

    private Future<BulkResponse> doPersistBulkLogic(List<EntityHolder<Object>> list,
                                                    Function<EntityHolder<?>, BulkOperation> persistLogic) {

        BulkRequest.Builder br = new BulkRequest.Builder();

        List<BulkOperation> bulkOperations = new ArrayList<>(list.size());
        for(EntityHolder<?> entityHolder : list){
            bulkOperations.add(persistLogic.apply(entityHolder));
        }

        if(bulkOperations.isEmpty()){
            return Future.failedFuture(new IllegalArgumentException("No items found to create bulk request for"));
        }

        br.operations(bulkOperations);

        return crudServiceTemplate.toFuture(esAsyncClient.bulk(br.build())).compose(bulkResponse -> {
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

    private String extractTenant(Object object, String tenantIdFieldName){
        Object data = (object instanceof FastestType ? ((FastestType) object).data() : object);
        if(data instanceof RawJson rawJson){
            try {
                Map<?,?> converted = objectMapper.readValue(rawJson.data(), Map.class);
                return (String) converted.get(tenantIdFieldName);
            } catch (JacksonException e) {
                throw new IllegalStateException("RawJson could not be deserialized for sanity check",e);
            }

        } else if (data instanceof Map<?,?> map) {
            return (String) map.get(tenantIdFieldName);
        }else{
            throw new NotImplementedException("Pojo Multi tenancy check is not implemented yet");
        }
    }

    private String formatToPrintJson(Object object){
        Object data = (object instanceof FastestType ? ((FastestType) object).data() : object);
        try {
            if(data instanceof Map<?,?> map){
                return objectMapper.convertValue(map, ObjectNode.class).toPrettyString();
            }else if(data instanceof RawJson rawJson){
                return objectMapper.readValue(rawJson.data(), ObjectNode.class).toPrettyString();
            }else{
                return objectMapper.convertValue(data, ObjectNode.class).toPrettyString();
            }
        } catch (Exception e) {
            return data.toString();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T postProcessSaveOrUpdate(T entity, EntityHolder<?> entityHolder, Long primaryTerm, Long seqNo) {
        // All token buffers received will be converted to RawJson in the upsert preprocessor
        // This is done since it uses less memory for bulk operations
        // So we convert those cases back to a TokenBuffer before returning
        if(entityDefinition.isOptimisticLockingEnabled()){
            return (T) updateVersionForEntity(entityHolder.entity(),
                                              primaryTerm,
                                              seqNo,
                                              entity instanceof TokenBuffer
                                                      && entityHolder.entity() instanceof RawJson
            );
        }else{
            if(entity instanceof TokenBuffer
                    && entityHolder.entity() instanceof RawJson json){
                try {
                    ObjectNode node = (ObjectNode) objectMapper.readTree(json.data());
                    TokenBuffer buffer = new TokenBuffer(objectMapper._serializationContext(), false);
                    objectMapper.writeValue(buffer, node);
                    return (T) buffer;
                } catch (JacksonException e) {
                    throw new IllegalStateException(e);
                }
            }else {
                return (T) entityHolder.entity();
            }
        }
    }

    private <T> T updateVersionForEntity(T entity, Long primaryTerm, Long seqNo){
        return updateVersionForEntity(entity, primaryTerm, seqNo, false);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> T updateVersionForEntity(T entity, Long primaryTerm, Long seqNo, boolean convertRawJsonToTokenBuffer){
        String versionValue =  primaryTerm + ":" + seqNo;

        switch (entity) {
            case TokenBuffer buffer -> {
                try {
                    // Convert TokenBuffer to JSON tree
                    JsonNode node = objectMapper.readTree(buffer.asParser(objectMapper._deserializationContext()));

                    node.withObject(JsonPointer.empty())
                        .put(entityDefinition.getVersionFieldName(), versionValue);

                    // Serialize back to TokenBuffer
                    TokenBuffer updatedBuffer = new TokenBuffer(objectMapper._serializationContext(), false);
                    objectMapper.writeValue(updatedBuffer, node);

                    return (T) updatedBuffer;
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to update version in TokenBuffer", e);
                }
            }
            case Map map -> map.put(entityDefinition.getVersionFieldName(), versionValue);
            case RawJson rawJson -> {

                try {
                    ObjectNode node = (ObjectNode) objectMapper.readTree(rawJson.data());
                    node.put(entityDefinition.getVersionFieldName(), versionValue);

                    // All token buffers passed to save or update will receive a RawJson object do to how the upsert pre processor works
                    // So we convert if need be
                    if (convertRawJsonToTokenBuffer) {

                        TokenBuffer updatedBuffer = new TokenBuffer(objectMapper._serializationContext(), false);
                        objectMapper.writeValue(updatedBuffer, node);
                        return (T) updatedBuffer;
                    } else {

                        byte[] updatedData = objectMapper.writeValueAsBytes(node);
                        return (T) new RawJson(updatedData);
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to update version in RawJson", e);
                }
            }
            case null, default -> throw new IllegalArgumentException("Pojo Not Supported for Version");
        }
        return entity;
    }

    private Future<Void> validateContext(final EntityContext context){
        if(entityDefinition.getMultiTenancyType() == MultiTenancyType.SHARED){
            if(context.getParticipant() != null && context.getParticipant().getTenantId() != null) {

                // Check if tenant selection is trying to be used but not enabled
                if (ObjectUtils.isNotEmpty(context.getTenantSelection())
                        && !entityDefinition.isMultiTenantSelectionEnabled()) {

                    return Future.failedFuture(
                            new IllegalArgumentException("Multi-tenant access for this EntityDefinition %s is not enabled".formatted(
                                    entityDefinition.getName()))
                    );
                } else {
                    return Future.succeededFuture();
                }
            }else{
                return Future.failedFuture(new IllegalArgumentException("Participant with a TenantId is required when MultiTenancyType is SHARED"));
            }
        }else if(ObjectUtils.isNotEmpty(context.getTenantSelection())){
            // This check is here since continuum will allow any published service to be called.
            // So someone could call the admin service even though it is not enabled for this EntityDefinition
            // Multitenant access can only be enabled if MultiTenancyType.SHARED
            return Future.failedFuture(
                    new IllegalArgumentException("Multi-tenant access for this EntityDefinition %s is not enabled".formatted(
                            entityDefinition.getName()))
            );
        }else{
            return Future.succeededFuture();
        }
    }

    private Future<List<MultiGetOperation>> validate_ComposeIds_AddTenantsToContext(final List<TenantSpecificId> ids, EntityContext entityContext){
        if(entityDefinition.getMultiTenancyType() == MultiTenancyType.SHARED
                && entityDefinition.isMultiTenantSelectionEnabled()){

            if(entityContext.getParticipant() != null && entityContext.getParticipant().getTenantId() != null) {

                List<MultiGetOperation> ret = new ArrayList<>(ids.size());
                List<String> tenants = new ArrayList<>(ids.size());
                for (TenantSpecificId id : ids) {
                    MultiGetOperation.Builder builder = new MultiGetOperation.Builder();
                    builder.index(entityDefinition.getItemIndex())
                           .id(id.tenantId() + "-" + id.entityId())
                           .routing(id.tenantId());

                    ret.add(builder.build());
                    tenants.add(id.tenantId());
                }

                entityContext.setTenantSelection(tenants);
                return Future.succeededFuture(ret);

            }else{
                return Future.failedFuture(new IllegalArgumentException("Participant with a TenantId is required when MultiTenancyType is SHARED"));
            }
        }else{
            return Future.failedFuture(
                    new IllegalArgumentException("Multi-tenant access for this EntityDefinition %s is not enabled".formatted(
                            entityDefinition.getName()))
            );
        }
    }

}
