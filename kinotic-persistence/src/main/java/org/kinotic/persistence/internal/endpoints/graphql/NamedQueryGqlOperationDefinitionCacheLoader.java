package org.kinotic.persistence.internal.endpoints.graphql;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import graphql.language.OperationDefinition;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.apache.commons.text.WordUtils;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.NamedQueriesDefinition;
import org.kinotic.persistence.api.model.idl.PageC3Type;
import org.kinotic.persistence.api.model.idl.decorators.QueryDecorator;
import org.kinotic.persistence.api.services.EntitiesRepository;
import org.kinotic.persistence.api.services.NamedQueriesDefinitionService;
import org.kinotic.persistence.internal.api.services.EntityDefinitionDAO;
import org.kinotic.persistence.internal.api.services.sql.SqlQueryType;
import org.kinotic.persistence.internal.endpoints.graphql.datafetchers.PagedQueryDataFetcher;
import org.kinotic.persistence.internal.endpoints.graphql.datafetchers.QueryDataFetcher;
import org.kinotic.persistence.internal.utils.QueryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Created By Navíd Mitchell 🤪on 2/12/25
 */
@Component
@RequiredArgsConstructor
public class NamedQueryGqlOperationDefinitionCacheLoader implements AsyncCacheLoader<String, List<GqlOperationDefinition>> {
    private static final Logger log = LoggerFactory.getLogger(NamedQueryGqlOperationDefinitionCacheLoader.class);
    private static final Pageable CURSOR_PAGEABLE = Pageable.create(null, 25, null);
    private static final Pageable OFFSET_PAGEABLE = Pageable.create(0, 25, null);

    private final EntitiesRepository entitiesRepository;
    private final NamedQueriesDefinitionService namedQueriesDefinitionService;
    private final ObjectMapper objectMapper;
    private final EntityDefinitionDAO entityDefinitionDAO;
    private final SecurityContext securityContext;

    @Override
    public CompletableFuture<? extends List<GqlOperationDefinition>> asyncLoad(String key, Executor executor) {
        return securityContext.withElevatedAccess(() -> entityDefinitionDAO.findById(key))
                                  .thenApply(entityDefinition -> {
                                      Validate.notNull(entityDefinition, "No EntityDefinition found for key: " + key);
                                      return entityDefinition;
                                  })
                                  .thenComposeAsync(entityDefinition -> {
                                      NamedQueriesDefinition namedQueriesDefinition = securityContext
                                              .withElevatedAccess(() -> namedQueriesDefinitionService
                                                      .findByApplicationAndEntityDefinition(entityDefinition.getApplicationId(),
                                                                                            entityDefinition.getName()))
                                              .join();
                                      List<GqlOperationDefinition> ret;
                                      if(namedQueriesDefinition != null) {

                                          ret = new ArrayList<>(namedQueriesDefinition.getNamedQueries().size());
                                          for (FunctionDefinition queryDefinition : namedQueriesDefinition.getNamedQueries()) {

                                              QueryDecorator queryDecorator = queryDefinition.findDecorator(QueryDecorator.class);
                                              if(queryDecorator != null) {

                                                  List<GqlOperationDefinition> definitions = buildGqlOperationDefinitions(entityDefinition,
                                                                                                                          queryDefinition,
                                                                                                                          queryDecorator);

                                                  ret.addAll(definitions);
                                              }else{
                                                  log.debug("No QueryDecorator found for Named query {} in EntityDefinition {}. No GraphQL operation will be created.",
                                                            queryDefinition.getName(),
                                                            entityDefinition.getName());
                                              }
                                          }
                                      }else{
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
                   .dataFetcherDefinitionFunction(struct -> new QueryDataFetcher(entitiesRepository, queryName, struct.getId()));
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
               .dataFetcherDefinitionFunction(struct -> new PagedQueryDataFetcher(entitiesRepository,
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
               .dataFetcherDefinitionFunction(struct -> new PagedQueryDataFetcher(entitiesRepository,
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


}
