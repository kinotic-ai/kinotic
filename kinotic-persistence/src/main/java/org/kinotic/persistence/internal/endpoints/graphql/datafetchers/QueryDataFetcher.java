package org.kinotic.persistence.internal.endpoints.graphql.datafetchers;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import org.kinotic.structures.api.domain.EntityContext;
import org.kinotic.persistence.api.services.EntitiesService;
import org.kinotic.persistence.internal.api.services.sql.MapParameterHolder;
import org.kinotic.persistence.internal.endpoints.openapi.RoutingContextToEntityContextAdapter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 6/17/24.
 */
@SuppressWarnings("rawtypes")
@RequiredArgsConstructor
public class QueryDataFetcher implements DataFetcher<CompletableFuture<List<Map>>> {

    private final EntitiesService entitiesService;
    private final String queryName;
    private final String structureId;

    @Override
    public CompletableFuture<List<Map>> get(DataFetchingEnvironment environment) throws Exception {
        RoutingContext rc = environment.getGraphQlContext().get(RoutingContext.class);
        Objects.requireNonNull(rc);
        EntityContext ec = new RoutingContextToEntityContextAdapter(rc);

        return entitiesService.namedQuery(structureId,
                                          queryName,
                                          new MapParameterHolder(environment.getArguments()),
                                          Map.class,
                                          ec);
    }
}
