package org.kinotic.persistence.internal.endpoints.graphql;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import graphql.language.OperationDefinition;
import org.apache.commons.lang3.Validate;
import org.apache.commons.text.WordUtils;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.NamedQueriesDefinition;
import org.kinotic.persistence.api.model.idl.PageC3Type;
import org.kinotic.persistence.api.model.idl.decorators.QueryDecorator;
import org.kinotic.persistence.internal.api.repositories.EntityDefinitionRepository;
import org.kinotic.persistence.internal.api.repositories.NamedQueriesDefinitionRepository;
import org.kinotic.persistence.internal.api.services.EntitiesService;
import org.kinotic.persistence.internal.api.services.sql.SqlQueryType;
import org.kinotic.persistence.internal.cache.DefaultCaffeineCacheFactory;
import org.kinotic.persistence.internal.endpoints.graphql.datafetchers.PagedQueryDataFetcher;
import org.kinotic.persistence.internal.endpoints.graphql.datafetchers.QueryDataFetcher;
import org.kinotic.persistence.internal.utils.QueryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Owns the cache of named-query {@link GqlOperationDefinition}s, keyed by
 * {@code (organizationId, entityDefinitionId)}, and builds them on a miss.
 * Created By Navíd Mitchell 🤪on 2/12/25
 */
@Component
public class NamedQueryGqlOperationDefinitionCache {
    private static final Logger log = LoggerFactory.getLogger(NamedQueryGqlOperationDefinitionCache.class);
    private static final Pageable CURSOR_PAGEABLE = Pageable.create(null, 25, null);
    private static final Pageable OFFSET_PAGEABLE = Pageable.create(0, 25, null);

    private final EntitiesService entitiesService;
    private final NamedQueriesDefinitionRepository namedQueriesDefinitionRepository;
    private final ObjectMapper objectMapper;
    private final EntityDefinitionRepository entityDefinitionRepository;
    private final AsyncLoadingCache<CacheKey, List<GqlOperationDefinition>> cache;

    public NamedQueryGqlOperationDefinitionCache(EntitiesService entitiesService,
                                                       NamedQueriesDefinitionRepository namedQueriesDefinitionRepository,
                                                       ObjectMapper objectMapper,
                                                       EntityDefinitionRepository entityDefinitionRepository,
                                                       DefaultCaffeineCacheFactory cacheFactory) {
        this.entitiesService = entitiesService;
        this.namedQueriesDefinitionRepository = namedQueriesDefinitionRepository;
        this.objectMapper = objectMapper;
        this.entityDefinitionRepository = entityDefinitionRepository;

        this.cache = cacheFactory.<CacheKey, List<GqlOperationDefinition>>newBuilder()
                                 .name("namedQueryOperationDefinitionCache")
                                 .expireAfterAccess(Duration.ofHours(1))
                                 .maximumSize(2000)
                                 .buildAsync(this::load);
    }

    /**
     * Returns the named-query {@link GqlOperationDefinition}s for {@code entityDefinitionId}
     * within {@code organizationId}, building and caching them on a miss.
     */
    public CompletableFuture<List<GqlOperationDefinition>> get(String organizationId, String entityDefinitionId) {
        return cache.get(new CacheKey(organizationId, entityDefinitionId));
    }

    /**
     * Evicts the cached operation definitions for {@code entityDefinitionId} within {@code organizationId}.
     */
    public void evict(String organizationId, String entityDefinitionId) {
        cache.asMap().remove(new CacheKey(organizationId, entityDefinitionId));
    }

    private CompletableFuture<List<GqlOperationDefinition>> load(CacheKey key, Executor executor) {
        return entityDefinitionRepository.findById(key.entityDefinitionId(), key.organizationId())
                .thenApply(entityDefinition -> {
                    Validate.notNull(entityDefinition, "No EntityDefinition found for %s", key);
                    return entityDefinition;
                })
                .thenComposeAsync(entityDefinition -> {
                    NamedQueriesDefinition namedQueriesDefinition = namedQueriesDefinitionRepository
                            .findByApplicationAndEntityDefinition(entityDefinition.getApplicationId(),
                                                                  entityDefinition.getName(),
                                                                  entityDefinition.getOrganizationId())
                            .join();
                    List<GqlOperationDefinition> ret;
                    if (namedQueriesDefinition != null) {

                        ret = new ArrayList<>(namedQueriesDefinition.getNamedQueries().size());
                        for (FunctionDefinition queryDefinition : namedQueriesDefinition.getNamedQueries()) {

                            QueryDecorator queryDecorator = queryDefinition.findDecorator(QueryDecorator.class);
                            if (queryDecorator != null) {

                                List<GqlOperationDefinition> definitions = buildGqlOperationDefinitions(entityDefinition,
                                                                                                        queryDefinition,
                                                                                                        queryDecorator);

                                ret.addAll(definitions);
                            } else {
                                log.debug("No QueryDecorator found for Named query {} in EntityDefinition {}. No GraphQL operation will be created.",
                                          queryDefinition.getName(),
                                          entityDefinition.getName());
                            }
                        }
                    } else {
                        ret = List.of();
                    }
                    return CompletableFuture.completedFuture(ret);
                }, executor);
    }

    private List<GqlOperationDefinition> buildGqlOperationDefinitions(EntityDefinition entityDefinition,
                                                                      FunctionDefinition queryDefinition,
                                                                      QueryDecorator queryDecorator) {
        List<GqlOperationDefinition> ret = new ArrayList<>();
        String queryName = queryDefinition.getName();
        SqlQueryType queryType = QueryUtils.determineQueryType(queryDecorator.getStatements());

        // If we return a page then we can potentially have multiple operations
        if(queryDefinition.getReturnType() instanceof PageC3Type) {
            switch (queryType) {
                case AGGREGATE:
                    ret.add(createForCursorPageQuery(entityDefinition, queryDefinition, queryName, ""));
                    break;
                case SELECT:
                    ret.add(createForCursorPageQuery(entityDefinition, queryDefinition, queryName, "WithCursor"));
                    ret.add(createForOffsetPageQuery(entityDefinition, queryDefinition, queryName));
                    break;
                default:
                    log.warn("The Page Return type is not valid for a {} query. Query {} will be skipped.", queryType, queryName);
                    break;
            }
        }else{
            GqlOperationDefinition.GqlOperationDefinitionBuilder builder = GqlOperationDefinition.builder();
            builder.operationName(queryName + WordUtils.capitalize(entityDefinition.getName()))
                   .operationType(getGqlOperationType(queryType))
                   .fieldDefinitionFunction(new QueryGqlFieldDefinitionFunction(queryDefinition, false))
                   .dataFetcherDefinitionFunction(struct -> new QueryDataFetcher(entitiesService, queryName, struct.getId()));
            ret.add(builder.build());
        }
        return ret;
    }

    private GqlOperationDefinition createForCursorPageQuery(EntityDefinition entityDefinition,
                                                            FunctionDefinition queryDefinition,
                                                            String queryName,
                                                            String suffix) {
        GqlOperationDefinition.GqlOperationDefinitionBuilder builder = GqlOperationDefinition.builder();
        builder.operationName(queryName + suffix + WordUtils.capitalize(entityDefinition.getName()))
               .operationType(OperationDefinition.Operation.QUERY)
               .fieldDefinitionFunction(new QueryGqlFieldDefinitionFunction(queryDefinition, true))
               .dataFetcherDefinitionFunction(struct -> new PagedQueryDataFetcher(entitiesService,
                                                                                  objectMapper,
                                                                                  queryDefinition,
                                                                                  CURSOR_PAGEABLE,
                                                                                  struct.getId()));
        return builder.build();
    }

    private GqlOperationDefinition createForOffsetPageQuery(EntityDefinition entityDefinition,
                                                            FunctionDefinition queryDefinition,
                                                            String queryName) {
        GqlOperationDefinition.GqlOperationDefinitionBuilder builder = GqlOperationDefinition.builder();
        builder.operationName(queryName + WordUtils.capitalize(entityDefinition.getName()))
               .operationType(OperationDefinition.Operation.QUERY)
               .fieldDefinitionFunction(new QueryGqlFieldDefinitionFunction(queryDefinition, false))
               .dataFetcherDefinitionFunction(struct -> new PagedQueryDataFetcher(entitiesService,
                                                                                  objectMapper,
                                                                                  queryDefinition,
                                                                                  OFFSET_PAGEABLE,
                                                                                  struct.getId()));
        return builder.build();
    }

    private static OperationDefinition.Operation getGqlOperationType(SqlQueryType queryType) {
        return switch (queryType) {
            case AGGREGATE, SELECT -> OperationDefinition.Operation.QUERY;
            case DELETE, INSERT, UPDATE -> OperationDefinition.Operation.MUTATION;
        };
    }

    private record CacheKey(String organizationId, String entityDefinitionId) {}

}
