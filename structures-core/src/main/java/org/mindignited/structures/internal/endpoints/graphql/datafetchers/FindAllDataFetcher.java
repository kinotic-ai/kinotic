package org.mindignited.structures.internal.endpoints.graphql.datafetchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.vertx.ext.web.RoutingContext;
import org.kinotic.continuum.core.api.crud.Page;
import org.kinotic.continuum.core.api.crud.Pageable;
import org.mindignited.structures.api.domain.EntityContext;
import org.mindignited.structures.api.services.EntitiesService;
import org.mindignited.structures.internal.endpoints.openapi.RoutingContextToEntityContextAdapter;
import org.mindignited.structures.internal.utils.GqlUtils;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/17/23.
 */
@SuppressWarnings("rawtypes")
public class FindAllDataFetcher implements DataFetcher<CompletableFuture<Page<Map>>> {

    private final String structureId;
    private final EntitiesService entitiesService;
    private final ObjectMapper objectMapper;

    public FindAllDataFetcher(String structureId,
                              EntitiesService entitiesService,
                              ObjectMapper objectMapper) {
        this.structureId = structureId;
        this.entitiesService = entitiesService;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public CompletableFuture<Page<Map>> get(DataFetchingEnvironment environment) throws Exception {
        RoutingContext rc = environment.getGraphQlContext().get(RoutingContext.class);
        Objects.requireNonNull(rc);
        EntityContext ec = new RoutingContextToEntityContextAdapter(rc);

        Pageable pageable =  GqlUtils.parseVariable(environment.getArguments(),
                                                    "pageable",
                                                    Pageable.class,
                                                    objectMapper);

        return entitiesService.findAll(structureId,
                                       pageable,
                                       Map.class,
                                       ec);
    }
}
