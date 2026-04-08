package org.kinotic.persistence.internal.endpoints.graphql.datafetchers;

import com.apollographql.federation.graphqljava._Entity;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.api.services.EntitiesRepository;
import org.kinotic.persistence.internal.endpoints.openapi.RoutingContextToEntityContextAdapter;
import org.kinotic.persistence.internal.utils.PersistenceUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Created by Navíd Mitchell 🤪 on 6/18/24.
 */
@SuppressWarnings("rawtypes")
@RequiredArgsConstructor
public class EntitiesDataFetcher implements DataFetcher<CompletableFuture<List<Map>>> {

    private final EntitiesRepository entitiesRepository;
    private final String application;

    @SuppressWarnings("unchecked")
    @Override
    public CompletableFuture<List<Map>> get(DataFetchingEnvironment env) throws Exception {
        List<Map<String, Object>> representations = env.getArgument(_Entity.argumentName);
        if(representations != null) {

            RoutingContext rc = env.getGraphQlContext().get(RoutingContext.class);
            Objects.requireNonNull(rc);
            EntityContext ec = new RoutingContextToEntityContextAdapter(rc);

            List<CompletableFuture<Map>> futures = new ArrayList<>(representations.size());
            // TODO: change this to mget
            for (Map<String, Object> representation : representations) {
                String typename = (String) representation.get("__typename");
                String id = (String) representation.get("id");
                String entityDefinitionId = PersistenceUtil.entityDefinitionNameToId(application, typename);
                futures.add(entitiesRepository.findById(entityDefinitionId,
                                                        id,
                                                        Map.class,
                                                        ec)
                                              .thenApply(entity -> new EntityMap(entity, typename)));
            }

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                    .thenApply(v -> futures.stream()
                                                           .map(CompletableFuture::join)
                                                           .collect(Collectors.toList()));

        }else{
            return CompletableFuture.completedFuture(List.of());
        }
    }

}
