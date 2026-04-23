package org.kinotic.persistence.internal.endpoints.graphql.datafetchers;

import org.kinotic.persistence.api.services.EntitiesRepository;
import tools.jackson.databind.ObjectMapper;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.vertx.ext.web.RoutingContext;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.internal.endpoints.openapi.RoutingContextToEntityContextAdapter;
import org.kinotic.persistence.internal.utils.GqlUtils;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/17/23.
 */
@SuppressWarnings("rawtypes")
public class FindAllDataFetcher implements DataFetcher<CompletableFuture<Page<Map>>> {

    private final String entityDefinitionId;
    private final EntitiesRepository entitiesRepository;
    private final ObjectMapper objectMapper;

    public FindAllDataFetcher(String entityDefinitionId,
                              EntitiesRepository entitiesRepository,
                              ObjectMapper objectMapper) {
        this.entityDefinitionId = entityDefinitionId;
        this.entitiesRepository = entitiesRepository;
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

        return entitiesRepository.findAll(entityDefinitionId,
                                          pageable,
                                          Map.class,
                                          ec);
    }
}
