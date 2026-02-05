package org.kinotic.structures.internal.endpoints.graphql.datafetchers;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.vertx.ext.web.RoutingContext;
import org.kinotic.structures.api.services.EntitiesService;
import org.kinotic.structures.internal.endpoints.openapi.RoutingContextToEntityContextAdapter;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/18/23.
 */
public class CountDataFetcher implements DataFetcher<CompletableFuture<Long>> {

    private final String structureId;
    private final EntitiesService entitiesService;

    public CountDataFetcher(String structureId, EntitiesService entitiesService) {
        this.structureId = structureId;
        this.entitiesService = entitiesService;
    }

    @Override
    public CompletableFuture<Long> get(DataFetchingEnvironment environment) throws Exception {
        RoutingContext rc = environment.getGraphQlContext().get(RoutingContext.class);
        Objects.requireNonNull(rc);

        return entitiesService.count(structureId, new RoutingContextToEntityContextAdapter(rc));
    }
}
