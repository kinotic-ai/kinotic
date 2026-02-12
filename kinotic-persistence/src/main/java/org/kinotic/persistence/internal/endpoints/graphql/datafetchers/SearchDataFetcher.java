package org.kinotic.persistence.internal.endpoints.graphql.datafetchers;

import tools.jackson.databind.ObjectMapper;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.vertx.ext.web.RoutingContext;
import org.kinotic.continuum.api.crud.Page;
import org.kinotic.continuum.api.crud.Pageable;
import org.kinotic.persistence.api.domain.EntityContext;
import org.kinotic.persistence.api.services.EntitiesService;
import org.kinotic.persistence.internal.endpoints.openapi.RoutingContextToEntityContextAdapter;
import org.kinotic.persistence.internal.utils.GqlUtils;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/17/23.
 */
@SuppressWarnings("rawtypes")
public class SearchDataFetcher implements DataFetcher<CompletableFuture<Page<Map>>> {

    private final String structureId;
    private final EntitiesService entitiesService;
    private final ObjectMapper objectMapper;

    public SearchDataFetcher(String structureId,
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

        String searchText = environment.getArgument("searchText");

        return entitiesService.search(structureId,
                                      searchText,
                                      pageable,
                                      Map.class,
                                      ec);
    }
}
