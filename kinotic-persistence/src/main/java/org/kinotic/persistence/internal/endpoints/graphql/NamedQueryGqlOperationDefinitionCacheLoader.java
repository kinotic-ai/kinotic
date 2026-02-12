package org.kinotic.persistence.internal.endpoints.graphql;

import tools.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import graphql.language.OperationDefinition;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.apache.commons.text.WordUtils;
import org.kinotic.rpc.api.crud.Pageable;
import org.kinotic.continuum.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.domain.NamedQueriesDefinition;
import org.kinotic.persistence.api.domain.Structure;
import org.kinotic.persistence.api.domain.idl.PageC3Type;
import org.kinotic.persistence.api.domain.idl.decorators.QueryDecorator;
import org.kinotic.persistence.api.services.EntitiesService;
import org.kinotic.persistence.api.services.NamedQueriesService;
import org.kinotic.persistence.internal.api.services.StructureDAO;
import org.kinotic.persistence.internal.endpoints.graphql.datafetchers.PagedQueryDataFetcher;
import org.kinotic.persistence.internal.endpoints.graphql.datafetchers.QueryDataFetcher;
import org.kinotic.persistence.internal.utils.QueryUtils;
import org.kinotic.persistence.internal.api.services.sql.SqlQueryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

    private final EntitiesService entitiesService;
    private final NamedQueriesService namedQueriesService;
    private final ObjectMapper objectMapper;
    private final StructureDAO structureDAO;

    @Override
    public CompletableFuture<? extends List<GqlOperationDefinition>> asyncLoad(String key, Executor executor) {
        return structureDAO.findById(key)
                           .thenApply(structure -> {
                               Validate.notNull(structure, "No Structure found for key: " + key);
                               return structure;
                           })
                           .thenComposeAsync(structure -> {
                               NamedQueriesDefinition namedQueriesDefinition = namedQueriesService.findByApplicationAndStructure(structure.getApplicationId(),
                                                                                                                               structure.getName())
                                                                                                  .join();
                               List<GqlOperationDefinition> ret;
                               if(namedQueriesDefinition != null) {

                                   ret = new ArrayList<>(namedQueriesDefinition.getNamedQueries().size());
                                   for (FunctionDefinition queryDefinition : namedQueriesDefinition.getNamedQueries()) {

                                       QueryDecorator queryDecorator = queryDefinition.findDecorator(QueryDecorator.class);
                                       if(queryDecorator != null) {

                                           List<GqlOperationDefinition> definitions = buildGqlOperationDefinitions(structure,
                                                                                                                   queryDefinition,
                                                                                                                   queryDecorator);

                                           ret.addAll(definitions);
                                       }else{
                                           log.debug("No QueryDecorator found for Named query {} in Structure {}. No GraphQL operation will be created.",
                                                    queryDefinition.getName(),
                                                    structure.getName());
                                       }
                                   }
                               }else{
                                   ret = List.of();
                               }
                               return CompletableFuture.completedFuture(ret);
                           }, executor);
    }

    private List<GqlOperationDefinition> buildGqlOperationDefinitions(Structure structure,
                                                                      FunctionDefinition queryDefinition,
                                                                      QueryDecorator queryDecorator) {
        List<GqlOperationDefinition> ret = new ArrayList<>();
        String queryName = queryDefinition.getName();
        SqlQueryType queryType = QueryUtils.determineQueryType(queryDecorator.getStatements());

        // If we return a page then we can potentially have multiple operations
        if(queryDefinition.getReturnType() instanceof PageC3Type) {
            switch (queryType) {
                case AGGREGATE:
                    ret.add(createForCursorPageQuery(structure, queryDefinition, queryName, ""));
                    break;
                case SELECT:
                    ret.add(createForCursorPageQuery(structure, queryDefinition, queryName, "WithCursor"));
                    ret.add(createForOffsetPageQuery(structure, queryDefinition, queryName));
                    break;
                default:
                    log.warn("The Page Return type is not valid for a {} query. Query {} will be skipped.", queryType, queryName);
                    break;
            }
        }else{
            GqlOperationDefinition.GqlOperationDefinitionBuilder builder = GqlOperationDefinition.builder();
            builder.operationName(queryName + WordUtils.capitalize(structure.getName()))
                   .operationType(getGqlOperationType(queryType))
                   .fieldDefinitionFunction(new QueryGqlFieldDefinitionFunction(queryDefinition, false))
                   .dataFetcherDefinitionFunction(struct -> new QueryDataFetcher(entitiesService, queryName, struct.getId()));
            ret.add(builder.build());
        }
        return ret;
    }

    private GqlOperationDefinition createForCursorPageQuery(Structure structure,
                                                            FunctionDefinition queryDefinition,
                                                            String queryName,
                                                            String suffix) {
        GqlOperationDefinition.GqlOperationDefinitionBuilder builder = GqlOperationDefinition.builder();
        builder.operationName(queryName + suffix + WordUtils.capitalize(structure.getName()))
               .operationType(OperationDefinition.Operation.QUERY)
               .fieldDefinitionFunction(new QueryGqlFieldDefinitionFunction(queryDefinition, true))
               .dataFetcherDefinitionFunction(struct -> new PagedQueryDataFetcher(entitiesService,
                                                                                  objectMapper,
                                                                                  queryDefinition,
                                                                                  CURSOR_PAGEABLE,
                                                                                  struct.getId()));
        return builder.build();
    }

    private GqlOperationDefinition createForOffsetPageQuery(Structure structure,
                                                            FunctionDefinition queryDefinition,
                                                            String queryName) {
        GqlOperationDefinition.GqlOperationDefinitionBuilder builder = GqlOperationDefinition.builder();
        builder.operationName(queryName + WordUtils.capitalize(structure.getName()))
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


}
