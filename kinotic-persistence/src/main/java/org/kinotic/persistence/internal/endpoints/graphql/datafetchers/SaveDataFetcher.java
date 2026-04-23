package org.kinotic.persistence.internal.endpoints.graphql.datafetchers;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.vertx.ext.web.RoutingContext;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.api.services.EntitiesRepository;
import org.kinotic.persistence.internal.endpoints.openapi.RoutingContextToEntityContextAdapter;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/18/23.
 */
@SuppressWarnings("rawtypes")
public class SaveDataFetcher implements DataFetcher<CompletableFuture<Map>> {

    private final String entityDefinitionId;
    private final EntitiesRepository entitiesRepository;

    public SaveDataFetcher(String entityDefinitionId, EntitiesRepository entitiesRepository) {
        this.entityDefinitionId = entityDefinitionId;
        this.entitiesRepository = entitiesRepository;
    }

    @Override
    public CompletableFuture<Map> get(DataFetchingEnvironment environment) throws Exception {
        RoutingContext rc = environment.getGraphQlContext().get(RoutingContext.class);
        Objects.requireNonNull(rc);
        EntityContext ec = new RoutingContextToEntityContextAdapter(rc);

        Map entity = environment.getArgument("input");

        return entitiesRepository.save(entityDefinitionId, entity, ec);
    }
}
