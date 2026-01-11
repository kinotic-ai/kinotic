package org.mindignited.structures.internal.api.hooks;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import org.mindignited.structures.api.config.StructuresProperties;
import org.mindignited.structures.api.domain.EntityContext;
import org.mindignited.structures.api.domain.Structure;
import org.mindignited.structures.api.domain.idl.decorators.MultiTenancyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Keeps track of all read operations pre-processors for a given structure.
 * This allows for the logic path to only call the pre-processors that are needed for a given operation and structure.
 * WARNING: This class does not validate any of the security or expected constraints defined for Structures. This is meant to be used after those operations are performed.
 * Created by Navíd Mitchell 🤪on 6/13/23.
 */
@Component
public class ReadPreProcessor {
    private static final Logger log = LoggerFactory.getLogger(ReadPreProcessor.class);

    private final StructuresProperties structuresProperties;

    public ReadPreProcessor(StructuresProperties structuresProperties) {
        this.structuresProperties = structuresProperties;
    }

    public void beforeCount(Structure structure,
                            String query,
                            CountRequest.Builder builder,
                            EntityContext context) {

        Query.Builder queryBuilder = createQueryWithTenantLogicAndSearch(structure, query, context, builder::routing);

        if(queryBuilder != null){
            builder.query(queryBuilder.build());
        }
    }

    public void beforeDelete(Structure structure,
                             DeleteRequest.Builder builder,
                             EntityContext context) {

        builder.refresh(Refresh.True);

        // add multi tenancy filters if needed
        if(structure.getMultiTenancyType() == MultiTenancyType.SHARED){
            if(context.hasTenantSelection()){
                builder.routing(context.getTenantSelection().getFirst());
            }else{
                builder.routing(context.getParticipant().getTenantId());
            }
        }
    }

    public void beforeDeleteByQuery(Structure structure,
                                    String query,
                                    DeleteByQueryRequest.Builder builder,
                                    EntityContext context) {

        Query.Builder queryBuilder = createQueryWithTenantLogicAndSearch(structure, query, context, builder::routing);

        if(queryBuilder != null){
            builder.query(queryBuilder.build());
        }
    }

    public void beforeFindAll(Structure structure,
                              SearchRequest.Builder builder,
                              EntityContext context) {

        Query.Builder queryBuilder = createQueryWithTenantLogic(structure, context, builder::routing);

        addSourceFilter(structure, builder, context);

        if(queryBuilder != null){
            builder.query(queryBuilder.build());
        }

        if(structure.isOptimisticLockingEnabled()){
            builder.seqNoPrimaryTerm(true);
        }
    }

    public void beforeFindById(Structure structure,
                               GetRequest.Builder builder,
                               EntityContext context){

        // add multi tenancy filters if needed
        if(structure.getMultiTenancyType() == MultiTenancyType.SHARED){
            if(context.hasTenantSelection()){
                builder.routing(context.getTenantSelection().getFirst());
            }else{
                builder.routing(context.getParticipant().getTenantId());
                if(!structure.isMultiTenantSelectionEnabled()) {
                    builder.sourceExcludes(structuresProperties.getTenantIdFieldName());
                }
            }
        }

        if(context.hasIncludedFieldsFilter()){
            builder.sourceIncludes(context.getIncludedFieldsFilter());
        }
    }


    public void beforeFindByIds(Structure structure,
                                MgetRequest.Builder builder,
                                EntityContext context){

        if(structure.getMultiTenancyType() == MultiTenancyType.SHARED
            && !structure.isMultiTenantSelectionEnabled()){
            builder.sourceExcludes(structuresProperties.getTenantIdFieldName());
        }

        if(context.hasIncludedFieldsFilter()){
            builder.sourceIncludes(context.getIncludedFieldsFilter());
        }
    }

    public void beforeSearch(Structure structure,
                             String searchText,
                             SearchRequest.Builder builder,
                             EntityContext context) {

        Query.Builder queryBuilder = createQueryWithTenantLogicAndSearch(structure, searchText, context, builder::routing);

        addSourceFilter(structure, builder, context);

        builder.query(queryBuilder.build());

        if(structure.isOptimisticLockingEnabled()){
            builder.seqNoPrimaryTerm(true);
        }
    }

    public Query.Builder createQueryWithTenantLogic(Structure structure, EntityContext context, Consumer<String> routingConsumer) {
        Query.Builder queryBuilder = null;
        // add multi tenancy filters if needed
        if(structure.getMultiTenancyType() == MultiTenancyType.SHARED){

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
                queryBuilder.bool(b -> b.filter(qb -> qb.terms(tsq -> tsq.field(structure.getTenantIdFieldName())
                                                                         .terms(tqf-> tqf.value(fieldValues)))));
            }else{

                routingConsumer.accept(context.getParticipant().getTenantId());
                queryBuilder = new Query.Builder();
                queryBuilder
                        .bool(b -> b.filter(qb -> qb.term(tq -> tq.field(structuresProperties.getTenantIdFieldName())
                                                                  .value(context.getParticipant().getTenantId()))));
            }
        }
        return queryBuilder;
    }

    public Query.Builder createQueryWithTenantLogicAndSearch(Structure structure,
                                                             String searchText,
                                                             EntityContext context,
                                                             Consumer<String> routingConsumer) {
        Query.Builder queryBuilder;
        if(searchText != null){
            queryBuilder = new Query.Builder();
            // add multi tenancy filters if needed
            if(structure.getMultiTenancyType() == MultiTenancyType.SHARED){

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
                                        .filter(qb -> qb.terms(tsq -> tsq.field(structure.getTenantIdFieldName())
                                                                         .terms(tqf-> tqf.value(fieldValues)))));

                }else{

                    routingConsumer.accept(context.getParticipant().getTenantId());
                    queryBuilder
                            .bool(b -> b.must(must -> must.queryString(qs -> qs.query(searchText).analyzeWildcard(true)))
                                        .filter(qb -> qb.term(tq -> tq.field(structuresProperties.getTenantIdFieldName())
                                                                      .value(context.getParticipant().getTenantId()))));
                }
            }else{
                queryBuilder.queryString(qs -> qs.query(searchText).analyzeWildcard(true));
            }
        }else{
            queryBuilder = createQueryWithTenantLogic(structure, context, routingConsumer);
        }
        return queryBuilder;
    }

    private void addSourceFilter(Structure structure, SearchRequest.Builder builder, EntityContext context) {
        // Set source fields filters
        builder.source(b -> b.filter(sf -> {
            // TODO: Put this back when no longer applicable
            // If MultiTenancyType.SHARED exclude tenant id
//            if(structure.getMultiTenancyType() == MultiTenancyType.SHARED) {
//                // Currently this must not be done to support our multi tenancy paranoid check
//                sf.excludes(structuresProperties.getTenantIdFieldName());
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
                if(structure.getMultiTenancyType() == MultiTenancyType.SHARED) {
                    sf.includes(structuresProperties.getTenantIdFieldName());
                }
            }
            return sf;
        }));
    }

}
