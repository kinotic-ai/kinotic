package org.kinotic.persistence.internal.endpoints.graphql.datafetchers;

import tools.jackson.databind.ObjectMapper;
import graphql.ExecutionResult;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.vertx.ext.web.RoutingContext;
import org.kinotic.continuum.core.api.crud.Page;
import org.kinotic.continuum.core.api.crud.Pageable;
import org.kinotic.continuum.idl.api.schema.FunctionDefinition;
import org.kinotic.continuum.idl.api.schema.ParameterDefinition;
import org.kinotic.structures.api.domain.EntityContext;
import org.kinotic.structures.api.domain.Structure;
import org.kinotic.structures.api.domain.idl.PageableC3Type;
import org.kinotic.persistence.api.services.EntitiesService;
import org.kinotic.persistence.internal.api.services.sql.MapParameterHolder;
import org.kinotic.persistence.internal.endpoints.openapi.RoutingContextToEntityContextAdapter;
import org.kinotic.persistence.internal.utils.GqlUtils;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 6/13/24.
 */
@SuppressWarnings("rawtypes")
public class PagedQueryDataFetcher implements DataFetcher<CompletableFuture<Page<Map>>> {

    private final Pageable defaultPageable;
    private final EntitiesService entitiesService;
    private final ObjectMapper objectMapper;
    private final String pageableName;
    private final String queryName;
    private final String structureId;

    /**
     * Creates a {@link DataFetcher} that will execute a named query page and return the result as a {@link ExecutionResult}
     * @param entitiesService the {@link EntitiesService} to use to execute the query
     * @param objectMapper the {@link ObjectMapper} to use to deserialize the input parameters
     * @param queryDefinition the {@link FunctionDefinition} for the query to execute
     * @param defaultPageable the default {@link Pageable} to use if no pageable parameter is defined in the {@link FunctionDefinition}
     * @param structureId the id of the {@link Structure} that the query is for
     */
    public PagedQueryDataFetcher(EntitiesService entitiesService,
                                 ObjectMapper objectMapper,
                                 FunctionDefinition queryDefinition,
                                 Pageable defaultPageable,
                                 String structureId) {
        this.defaultPageable = defaultPageable;
        this.entitiesService = entitiesService;
        this.objectMapper = objectMapper;
        this.queryName = queryDefinition.getName();
        this.structureId = structureId;

        String pageParamName = null;
        for(ParameterDefinition parameter : queryDefinition.getParameters()){
            if(parameter.getType() instanceof PageableC3Type){
                pageParamName = parameter.getName();
                break;
            }
        }
        pageableName = pageParamName;
    }

    @Override
    public CompletableFuture<Page<Map>> get(DataFetchingEnvironment environment) throws Exception {
        RoutingContext rc = environment.getGraphQlContext().get(RoutingContext.class);
        Objects.requireNonNull(rc);
        EntityContext ec = new RoutingContextToEntityContextAdapter(rc);

        Pageable pageable;
        if(pageableName != null) {
            pageable = GqlUtils.parseVariable(environment.getArguments(),
                                              pageableName,
                                              Pageable.class,
                                              objectMapper);
        }else {
            pageable = defaultPageable;
        }
        return entitiesService.namedQueryPage(structureId,
                                              queryName,
                                              new MapParameterHolder(environment.getArguments()),
                                              pageable,
                                              Map.class,
                                              ec);
    }
}
