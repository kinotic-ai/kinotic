package org.kinotic.persistence.internal.api.services.sql.executors;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.kinotic.rpc.api.crud.Page;
import org.kinotic.rpc.api.crud.Pageable;
import org.kinotic.persistence.api.config.StructuresProperties;
import org.kinotic.persistence.api.domain.Structure;
import org.kinotic.persistence.api.domain.idl.decorators.MultiTenancyType;
import org.kinotic.persistence.internal.api.services.sql.QueryContext;
import org.kinotic.persistence.internal.api.services.sql.elasticsearch.ElasticVertxClient;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Created by Navíd Mitchell 🤪 on 4/28/24.
 */
public class AggregateQueryExecutor extends AbstractQueryExecutor {

    private final ElasticVertxClient elasticVertxClient;
    private final String statement;
    private final StructuresProperties structuresProperties;

    public AggregateQueryExecutor(Structure structure,
                                  ElasticVertxClient elasticVertxClient,
                                  String statement,
                                  StructuresProperties structuresProperties) {
        super(structure);
        this.elasticVertxClient = elasticVertxClient;
        this.statement = statement;
        this.structuresProperties = structuresProperties;

    }

    @Override
    public <T> CompletableFuture<List<T>> execute(QueryContext context, Class<T> type) {

        return executePage(context, null, type)
                                 .thenApply(Page::getContent);
    }

    @Override
    public <T> CompletableFuture<Page<T>> executePage(QueryContext context,
                                                      Pageable pageable,
                                                      Class<T> type) {
        JsonObject filter = createFilterIfNeeded(context);

        return elasticVertxClient.querySql(statement,
                                           context.getQueryParameters(),
                                           filter,
                                           context.getQueryOptions(),
                                           pageable,
                                           type);
    }

    private JsonObject createFilterIfNeeded(QueryContext context) {
        JsonObject filter = null;
        // add multi tenancy filters if needed
        if(structure.getMultiTenancyType() == MultiTenancyType.SHARED) {

            if(structure.isMultiTenantSelectionEnabled() && context.getEntityContext().hasTenantSelection()){

                // Filter must fit the Query DSL format, and look like the following
                //     "bool":{
                //         "filter":[
                //         {
                //             "terms":{
                //                  "tenantId": ["tenant1", "tenant2", "tenant3"]
                //              }
                //         },
                //       ]
                //     }

                JsonArray tenants = new JsonArray(context.getEntityContext().getTenantSelection());
                filter = new JsonObject().put("bool", new JsonObject()
                        .put("filter", new JsonArray()
                                .add(new JsonObject().put("terms", new JsonObject()
                                        .put(structure.getTenantIdFieldName(), tenants)))
                        ));

            }else if(!structure.isMultiTenantSelectionEnabled() && context.getEntityContext().hasTenantSelection()){
                throw new IllegalArgumentException("Tenant selection is not supported for this structure");
            }else{

                // Filter must fit the Query DSL format, and look like the following
                //     "bool":{
                //         "filter":[
                //         {
                //             "term":{
                //                  "structuresTenantId":{
                //                      "value":"kinotic"
                //                  }
                //             }
                //         },
                //         {
                //             "terms": {
                //                  "_routing": ["kinotic"]
                //              }
                //         }
                //       ]
                //     }

                String tenantId = context.getEntityContext().getParticipant().getTenantId();
                filter = new JsonObject().put("bool", new JsonObject()
                        .put("filter", new JsonArray()
                                .add(new JsonObject().put("term", new JsonObject()
                                        .put(structuresProperties.getTenantIdFieldName(), new JsonObject()
                                                .put("value", tenantId))))
                                .add(new JsonObject().put("terms", new JsonObject()
                                        .put("_routing", new JsonArray().add(tenantId))))
                        ));
            }

        }
        return filter;
    }
}
