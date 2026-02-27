package org.kinotic.persistence.internal.endpoints.graphql.datafetchers;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.vertx.ext.web.RoutingContext;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.api.services.EntitiesService;
import org.kinotic.persistence.internal.endpoints.openapi.RoutingContextToEntityContextAdapter;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/18/23.
 */
public class SyncIndexDataFetcher implements DataFetcher<CompletableFuture<String>> {

    private final String entityDefinitionId;
    private final EntitiesService entitiesService;

    public SyncIndexDataFetcher(String entityDefinitionId, EntitiesService entitiesService) {
        this.entityDefinitionId = entityDefinitionId;
        this.entitiesService = entitiesService;
    }

    @Override
    public CompletableFuture<String> get(DataFetchingEnvironment environment) throws Exception {
        RoutingContext rc = environment.getGraphQlContext().get(RoutingContext.class);
        Objects.requireNonNull(rc);
        EntityContext ec = new RoutingContextToEntityContextAdapter(rc);

        return entitiesService.syncIndex(entityDefinitionId, ec)
                              .thenCompose(aVoid -> CompletableFuture.completedFuture(entityDefinitionId));
    }
}
