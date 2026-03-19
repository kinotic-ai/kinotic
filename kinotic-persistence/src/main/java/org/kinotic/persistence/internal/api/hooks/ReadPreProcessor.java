package org.kinotic.persistence.internal.api.hooks;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import org.kinotic.persistence.api.config.PersistenceProperties;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.idl.decorators.MultiTenancyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Keeps track of all read operations pre-processors for a given EntityDefinition.
 * This allows for the logic path to only call the pre-processors that are needed for a given operation and EntityDefinition.
 * WARNING: This class does not validate any of the security or expected constraints defined for EntityDefinitions. This is meant to be used after those operations are performed.
 * Created by Navíd Mitchell 🤪on 6/13/23.
 */
@Component
public class ReadPreProcessor {
    private static final Logger log = LoggerFactory.getLogger(ReadPreProcessor.class);

    private final PersistenceProperties persistenceProperties;

    public ReadPreProcessor(PersistenceProperties persistenceProperties) {
        this.persistenceProperties = persistenceProperties;
    }

    public void beforeCount(EntityDefinition entityDefinition,
                            String query,
                            CountRequest.Builder builder,
                            EntityContext context) {

        Query.Builder queryBuilder = createQueryWithTenantLogicAndSearch(entityDefinition, query, context, builder::routing);

        if(queryBuilder != null){
            builder.query(queryBuilder.build());
        }
    }

    public void beforeDelete(EntityDefinition entityDefinition,
                             DeleteRequest.Builder builder,
                             EntityContext context) {

        builder.refresh(Refresh.True);

        // add multi tenancy filters if needed
        if(entityDefinition.getMultiTenancyType() == MultiTenancyType.SHARED){
            if(context.hasTenantSelection()){
                builder.routing(context.getTenantSelection().getFirst());
            }else{
                builder.routing(context.getParticipant().getTenantId());
            }
        }
    }

    public void beforeDeleteByQuery(EntityDefinition entityDefinition,
                                    String query,
                                    DeleteByQueryRequest.Builder builder,
                                    EntityContext context) {

        Query.Builder queryBuilder = createQueryWithTenantLogicAndSearch(entityDefinition, query, context, builder::routing);

        if(queryBuilder != null){
            builder.query(queryBuilder.build());
        }
    }

    public void beforeFindAll(EntityDefinition entityDefinition,
                              SearchRequest.Builder builder,
                              EntityContext context) {

        Query.Builder queryBuilder = createQueryWithTenantLogic(entityDefinition, context, builder::routing);

        addSourceFilter(entityDefinition, builder, context);

        if(queryBuilder != null){
            builder.query(queryBuilder.build());
        }

        if(entityDefinition.isOptimisticLockingEnabled()){
            builder.seqNoPrimaryTerm(true);
        }
    }

    public void beforeFindById(EntityDefinition entityDefinition,
                               GetRequest.Builder builder,
                               EntityContext context){

        // add multi tenancy filters if needed
        if(entityDefinition.getMultiTenancyType() == MultiTenancyType.SHARED){
            if(context.hasTenantSelection()){
                builder.routing(context.getTenantSelection().getFirst());
            }else{
                builder.routing(context.getParticipant().getTenantId());
                if(!entityDefinition.isMultiTenantSelectionEnabled()) {
                    builder.sourceExcludes(persistenceProperties.getTenantIdFieldName());
                }
            }
        }

        if(context.hasIncludedFieldsFilter()){
            builder.sourceIncludes(context.getIncludedFieldsFilter());
        }
    }


    public void beforeFindByIds(EntityDefinition entityDefinition,
                                MgetRequest.Builder builder,
                                EntityContext context){

        if(entityDefinition.getMultiTenancyType() == MultiTenancyType.SHARED
            && !entityDefinition.isMultiTenantSelectionEnabled()){
            builder.sourceExcludes(persistenceProperties.getTenantIdFieldName());
        }

        if(context.hasIncludedFieldsFilter()){
            builder.sourceIncludes(context.getIncludedFieldsFilter());
        }
    }

    public void beforeSearch(EntityDefinition entityDefinition,
                             String searchText,
                             SearchRequest.Builder builder,
                             EntityContext context) {

        Query.Builder queryBuilder = createQueryWithTenantLogicAndSearch(entityDefinition, searchText, context, builder::routing);

        addSourceFilter(entityDefinition, builder, context);

        builder.query(queryBuilder.build());

        if(entityDefinition.isOptimisticLockingEnabled()){
            builder.seqNoPrimaryTerm(true);
        }
    }

    public Query.Builder createQueryWithTenantLogic(EntityDefinition entityDefinition, EntityContext context, Consumer<String> routingConsumer) {
        Query.Builder queryBuilder = null;
        // add multi tenancy filters if needed
        if(entityDefinition.getMultiTenancyType() == MultiTenancyType.SHARED){

            // Check if multiple tenants are selected if not use the logged-in user's tenant
            if(context.hasTenantSelection()) {
                List<String> multiTenantSelection = context.getTenantSelection();
                List<FieldValue> fieldValues = new ArrayList<>(multiTenantSelection.size());
                for(String tenantId : multiTenantSelection){
                    fieldValues.add(FieldValue.of(tenantId));
                }
                log.trace("Find All Multi tenant selection provided. Received {} tenants", multiTenantSelection.size());

                // We do not add routing since the data could be spread across multiple shards
                queryBuilder = new Query.Builder();
                queryBuilder.bool(b -> b.filter(qb -> qb.terms(tsq -> tsq.field(entityDefinition.getTenantIdFieldName())
                                                                         .terms(tqf-> tqf.value(fieldValues)))));
            }else{

                routingConsumer.accept(context.getParticipant().getTenantId());
                queryBuilder = new Query.Builder();
                queryBuilder
                        .bool(b -> b.filter(qb -> qb.term(tq -> tq.field(persistenceProperties.getTenantIdFieldName())
                                                                  .value(context.getParticipant().getTenantId()))));
            }
        }
        return queryBuilder;
    }

    public Query.Builder createQueryWithTenantLogicAndSearch(EntityDefinition entityDefinition,
                                                             String searchText,
                                                             EntityContext context,
                                                             Consumer<String> routingConsumer) {
        Query.Builder queryBuilder;
        if(searchText != null){
            queryBuilder = new Query.Builder();
            // add multi tenancy filters if needed
            if(entityDefinition.getMultiTenancyType() == MultiTenancyType.SHARED){

                // Check if multiple tenants are selected if not use the logged-in user's tenant
                if(context.hasTenantSelection()) {
                    List<String> multiTenantSelection = context.getTenantSelection();
                    List<FieldValue> fieldValues = new ArrayList<>(multiTenantSelection.size());
                    for(String tenantId : multiTenantSelection){
                        fieldValues.add(FieldValue.of(tenantId));
                    }
                    log.trace("Search Multi tenant selection provided. Received {} tenants", multiTenantSelection.size());

                    // We do not add routing since the data could be spread across multiple shards
                    queryBuilder
                            .bool(b -> b.must(must -> must.queryString(qs -> qs.query(searchText).analyzeWildcard(true)))
                                        .filter(qb -> qb.terms(tsq -> tsq.field(entityDefinition.getTenantIdFieldName())
                                                                         .terms(tqf-> tqf.value(fieldValues)))));

                }else{

                    routingConsumer.accept(context.getParticipant().getTenantId());
                    queryBuilder
                            .bool(b -> b.must(must -> must.queryString(qs -> qs.query(searchText).analyzeWildcard(true)))
                                        .filter(qb -> qb.term(tq -> tq.field(persistenceProperties.getTenantIdFieldName())
                                                                      .value(context.getParticipant().getTenantId()))));
                }
            }else{
                queryBuilder.queryString(qs -> qs.query(searchText).analyzeWildcard(true));
            }
        }else{
            queryBuilder = createQueryWithTenantLogic(entityDefinition, context, routingConsumer);
        }
        return queryBuilder;
    }

    private void addSourceFilter(EntityDefinition entityDefinition, SearchRequest.Builder builder, EntityContext context) {
        // Set source fields filters
        builder.source(b -> b.filter(sf -> {
            // TODO: Put this back when no longer applicable
            // If MultiTenancyType.SHARED exclude tenant id
//            if(entityDefinition.getMultiTenancyType() == MultiTenancyType.SHARED) {
//                // Currently this must not be done to support our multi tenancy paranoid check
//                sf.excludes(persistenceProperties.getTenantIdFieldName());
//            }
            // Add source fields filter
            if(context.hasIncludedFieldsFilter()){
                // If fields filter is empty  we exclude all fields from the source
                if(context.getIncludedFieldsFilter().isEmpty()) {
                    sf.excludes("*");
                }else {
                    sf.includes(context.getIncludedFieldsFilter());
                }
                // TODO: remove this when above is put back
                if(entityDefinition.getMultiTenancyType() == MultiTenancyType.SHARED) {
                    sf.includes(persistenceProperties.getTenantIdFieldName());
                }
            }
            return sf;
        }));
    }

}
