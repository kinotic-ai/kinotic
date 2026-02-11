package org.kinotic.persistence.internal.endpoints.graphql.datafetchers;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.vertx.ext.web.RoutingContext;
import org.kinotic.persistence.api.domain.EntityContext;
import org.kinotic.persistence.api.services.EntitiesService;
import org.kinotic.persistence.internal.endpoints.openapi.RoutingContextToEntityContextAdapter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/18/23.
 */
@SuppressWarnings("rawtypes")
public class BulkUpdateDataFetcher implements DataFetcher<CompletableFuture<Boolean>> {

    private final String structureId;
    private final EntitiesService entitiesService;

    public BulkUpdateDataFetcher(String structureId, EntitiesService entitiesService) {
        this.structureId = structureId;
        this.entitiesService = entitiesService;
    }

    @Override
    public CompletableFuture<Boolean> get(DataFetchingEnvironment environment) throws Exception {
        RoutingContext rc = environment.getGraphQlContext().get(RoutingContext.class);
        Objects.requireNonNull(rc);
        EntityContext ec = new RoutingContextToEntityContextAdapter(rc);

        List<Map> entity = environment.getArgument("input");

        return entitiesService.bulkUpdate(structureId, entity, ec)
                              .thenApply(v -> true);
    }
}
