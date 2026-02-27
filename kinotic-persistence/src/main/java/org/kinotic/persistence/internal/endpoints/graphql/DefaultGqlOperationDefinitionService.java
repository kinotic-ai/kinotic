package org.kinotic.persistence.internal.endpoints.graphql;

import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.internal.endpoints.graphql.datafetchers.*;
import tools.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import graphql.language.OperationDefinition;
import graphql.scalars.ExtendedScalars;
import graphql.schema.GraphQLFieldDefinition;
import lombok.extern.slf4j.Slf4j;

import org.kinotic.persistence.api.model.EntityOperation;
import org.kinotic.persistence.api.model.idl.decorators.EntityServiceDecorator;
import org.kinotic.persistence.api.model.idl.decorators.PolicyDecorator;
import org.kinotic.persistence.api.services.EntitiesService;
import org.kinotic.persistence.internal.cache.DefaultCaffeineCacheFactory;
import org.kinotic.persistence.internal.cache.events.CacheEvictionEvent;
import org.kinotic.persistence.internal.utils.GqlUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

import static graphql.Scalars.*;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLList.list;
import static graphql.schema.GraphQLNonNull.nonNull;

/**
 * Created by Navíd Mitchell 🤪 on 12/14/23.
 */
@Slf4j
@Component
public class DefaultGqlOperationDefinitionService implements GqlOperationDefinitionService {

    private final List<GqlOperationDefinition> builtInOperationDefinitions;
    private final AsyncLoadingCache<String, List<GqlOperationDefinition>> namedQueryOperationDefinitionCache;

    public DefaultGqlOperationDefinitionService(EntitiesService entitiesService,
                                                NamedQueryGqlOperationDefinitionCacheLoader namedQueryGqlOperationDefinitionCacheLoader,
                                                ObjectMapper objectMapper,
                                                DefaultCaffeineCacheFactory cacheFactory) {

        namedQueryOperationDefinitionCache
                = cacheFactory.<String, List<GqlOperationDefinition>>newBuilder()
                              .name("namedQueryOperationDefinitionCache")
                              .expireAfterAccess(Duration.ofHours(1))
                              .maximumSize(2000)
                              .buildAsync(namedQueryGqlOperationDefinitionCacheLoader);

        this.builtInOperationDefinitions = List.of(

                GqlOperationDefinition.builder()
                                      .operationName(EntityOperation.BULK_SAVE.methodName())
                                      .operationType(OperationDefinition.Operation.MUTATION)
                                      .fieldDefinitionFunction(args -> {

                                          GraphQLFieldDefinition.Builder builder = newFieldDefinition()
                                                  .name(EntityOperation.BULK_SAVE.methodName() + args.getEntityDefinitionName())
                                                  .type(GraphQLBoolean)
                                                  .argument(newArgument().name("input")
                                                                         .type(nonNull(list(nonNull(args.getInputType())))));

                                          builder = addPolicyIfPresent(builder, args.getEntityOperationsMap().get(EntityOperation.BULK_SAVE));
                                          return builder.build();
                                      })
                                      .dataFetcherDefinitionFunction(entityDefinition -> new BulkSaveDataFetcher(entityDefinition.getId(), entitiesService))
                                      .build(),

                GqlOperationDefinition.builder()
                                      .operationName(EntityOperation.BULK_UPDATE.methodName())
                                      .operationType(OperationDefinition.Operation.MUTATION)
                                      .fieldDefinitionFunction(args -> {

                                          GraphQLFieldDefinition.Builder builder = newFieldDefinition()
                                                  .name(EntityOperation.BULK_UPDATE.methodName() + args.getEntityDefinitionName())
                                                  .type(GraphQLBoolean)
                                                  .argument(newArgument().name("input")
                                                                         .type(nonNull(list(nonNull(args.getInputType())))));

                                          builder = addPolicyIfPresent(builder, args.getEntityOperationsMap().get(EntityOperation.BULK_UPDATE));
                                          return builder.build();
                                      })
                                      .dataFetcherDefinitionFunction(entityDefinition -> new BulkUpdateDataFetcher(entityDefinition.getId(), entitiesService))
                                      .build(),

                GqlOperationDefinition.builder()
                                      .operationName(EntityOperation.COUNT.methodName())
                                      .operationType(OperationDefinition.Operation.QUERY)
                                      .fieldDefinitionFunction(args -> {

                                          GraphQLFieldDefinition.Builder builder = newFieldDefinition()
                                                  .name(EntityOperation.COUNT.methodName() + args.getEntityDefinitionName())
                                                  .type(ExtendedScalars.GraphQLLong);

                                          builder = addPolicyIfPresent(builder, args.getEntityOperationsMap().get(EntityOperation.COUNT));
                                          return builder.build();
                                      })
                                      .dataFetcherDefinitionFunction(entityDefinition -> new CountDataFetcher(entityDefinition.getId(), entitiesService))
                                      .build(),

                GqlOperationDefinition.builder()
                                      .operationName("delete")
                                      .operationType(OperationDefinition.Operation.MUTATION)
                                      .fieldDefinitionFunction(args -> {

                                          GraphQLFieldDefinition.Builder builder = newFieldDefinition()
                                                  .name("delete" + args.getEntityDefinitionName())
                                                  .type(GraphQLID)
                                                  .argument(newArgument().name("id")
                                                                         .type(nonNull(GraphQLID)));

                                          builder = addPolicyIfPresent(builder, args.getEntityOperationsMap().get(EntityOperation.DELETE_BY_ID));
                                          return builder.build();
                                      })
                                      .dataFetcherDefinitionFunction(entityDefinition -> new DeleteDataFetcher(entityDefinition.getId(), entitiesService))
                                      .build(),

                GqlOperationDefinition.builder()
                                      .operationName(EntityOperation.FIND_BY_ID.methodName())
                                      .operationType(OperationDefinition.Operation.QUERY)
                                      .fieldDefinitionFunction(args -> {

                                          GraphQLFieldDefinition.Builder builder = newFieldDefinition()
                                                  .name(EntityOperation.FIND_BY_ID.methodName() + args.getEntityDefinitionName())
                                                  .type(args.getOutputType())
                                                  .argument(newArgument().name("id")
                                                                         .type(nonNull(GraphQLID)));

                                          builder = addPolicyIfPresent(builder, args.getEntityOperationsMap().get(EntityOperation.FIND_BY_ID));
                                          return builder.build();
                                      })
                                      .dataFetcherDefinitionFunction(entityDefinition -> new FindByIdDataFetcher(entityDefinition.getId(), entitiesService))
                                      .build(),

                GqlOperationDefinition.builder()
                                      .operationName(EntityOperation.FIND_ALL.methodName())
                                      .operationType(OperationDefinition.Operation.QUERY)
                                      .fieldDefinitionFunction(args -> {

                                          GraphQLFieldDefinition.Builder builder = newFieldDefinition()
                                                  .name(EntityOperation.FIND_ALL.methodName() + args.getEntityDefinitionName())
                                                  .type(nonNull(args.getPageResponseType()))
                                                  .argument(newArgument().name("pageable")
                                                                         .type(nonNull(args.getOffsetPageableReference())));

                                          builder = addPolicyIfPresent(builder, args.getEntityOperationsMap().get(EntityOperation.FIND_ALL));
                                          return builder.build();
                                      })
                                      .dataFetcherDefinitionFunction(entityDefinition -> new FindAllDataFetcher(entityDefinition.getId(), entitiesService, objectMapper))
                                      .build(),

                GqlOperationDefinition.builder()
                                      .operationName("findAllWithCursor")
                                      .operationType(OperationDefinition.Operation.QUERY)
                                      .fieldDefinitionFunction(args -> {

                                          GraphQLFieldDefinition.Builder builder = newFieldDefinition()
                                                  .name("findAllWithCursor" + args.getEntityDefinitionName())
                                                  .type(nonNull(args.getCursorPageResponseType()))
                                                  .argument(newArgument().name("pageable")
                                                                         .type(nonNull(args.getCursorPageableReference())));

                                          builder = addPolicyIfPresent(builder, args.getEntityOperationsMap().get(EntityOperation.FIND_ALL));
                                          return builder.build();
                                      })
                                      .dataFetcherDefinitionFunction(entityDefinition -> new FindAllDataFetcher(entityDefinition.getId(), entitiesService, objectMapper))
                                      .build(),

                GqlOperationDefinition.builder()
                                      .operationName(EntityOperation.SAVE.methodName())
                                      .operationType(OperationDefinition.Operation.MUTATION)
                                      .fieldDefinitionFunction(args -> {

                                          GraphQLFieldDefinition.Builder builder = newFieldDefinition()
                                                  .name(EntityOperation.SAVE.methodName() + args.getEntityDefinitionName())
                                                  .type(args.getOutputType())
                                                  .argument(newArgument().name("input")
                                                                         .type(nonNull(args.getInputType())));

                                          builder = addPolicyIfPresent(builder, args.getEntityOperationsMap().get(EntityOperation.SAVE));
                                          return builder.build();
                                      })
                                      .dataFetcherDefinitionFunction(entityDefinition -> new SaveDataFetcher(entityDefinition.getId(), entitiesService))
                                      .build(),

                GqlOperationDefinition.builder()
                                      .operationName(EntityOperation.SEARCH.methodName())
                                      .operationType(OperationDefinition.Operation.QUERY)
                                      .fieldDefinitionFunction(args -> {

                                          GraphQLFieldDefinition.Builder builder = newFieldDefinition()
                                                  .name(EntityOperation.SEARCH.methodName() + args.getEntityDefinitionName())
                                                  .type(nonNull(args.getPageResponseType()))
                                                  .argument(newArgument().name("searchText")
                                                                         .type(nonNull(GraphQLString)))
                                                  .argument(newArgument().name("pageable")
                                                                         .type(nonNull(args.getOffsetPageableReference())));

                                          builder = addPolicyIfPresent(builder, args.getEntityOperationsMap().get(EntityOperation.SEARCH));
                                          return builder.build();
                                      })
                                      .dataFetcherDefinitionFunction(entityDefinition -> new SearchDataFetcher(entityDefinition.getId(), entitiesService, objectMapper))
                                      .build(),

                GqlOperationDefinition.builder()
                                      .operationName("searchWithCursor")
                                      .operationType(OperationDefinition.Operation.QUERY)
                                      .fieldDefinitionFunction(args -> {

                                          GraphQLFieldDefinition.Builder builder = newFieldDefinition()
                                                  .name("searchWithCursor" + args.getEntityDefinitionName())
                                                  .type(nonNull(args.getCursorPageResponseType()))
                                                  .argument(newArgument().name("searchText")
                                                                         .type(nonNull(GraphQLString)))
                                                  .argument(newArgument().name("pageable")
                                                                         .type(nonNull(args.getCursorPageableReference())));

                                          builder = addPolicyIfPresent(builder, args.getEntityOperationsMap().get(EntityOperation.SEARCH));
                                          return builder.build();
                                      })
                                      .dataFetcherDefinitionFunction(entityDefinition -> new SearchDataFetcher(entityDefinition.getId(), entitiesService, objectMapper))
                                      .build(),


                GqlOperationDefinition.builder()
                                      .operationName("sync")
                                      .operationType(OperationDefinition.Operation.MUTATION)
                                      .fieldDefinitionFunction(args -> {

                                          GraphQLFieldDefinition.Builder builder = newFieldDefinition()
                                                  .name("sync" + args.getEntityDefinitionName())
                                                  .type(GraphQLID);

                                          builder = addPolicyIfPresent(builder, args.getEntityOperationsMap().get(EntityOperation.SYNC_INDEX));
                                          return builder.build();
                                      })
                                      .dataFetcherDefinitionFunction(entityDefinition -> new SyncIndexDataFetcher(entityDefinition.getId(), entitiesService))
                                      .build(),


                GqlOperationDefinition.builder()
                                      .operationName(EntityOperation.UPDATE.methodName())
                                      .operationType(OperationDefinition.Operation.MUTATION)
                                      .fieldDefinitionFunction(args -> {

                                          GraphQLFieldDefinition.Builder builder = newFieldDefinition()
                                                  .name(EntityOperation.UPDATE.methodName() + args.getEntityDefinitionName())
                                                  .type(args.getOutputType())
                                                  .argument(newArgument().name("input")
                                                                         .type(nonNull(args.getInputType())));

                                          builder = addPolicyIfPresent(builder, args.getEntityOperationsMap().get(EntityOperation.UPDATE));
                                          return builder.build();
                                      })
                                      .dataFetcherDefinitionFunction(entityDefinition -> new UpdateDataFetcher(entityDefinition.getId(), entitiesService))
                                      .build()
        );
    }


    /**
     * Evicts the caches for an application event.  This can be a modification or deletion of a named query or a entityDefinition.
     * @param cacheEvictionEvent the event containing the entityDefinition or named query to evict the caches for
     */
    @EventListener
    public void handleCacheEviction(CacheEvictionEvent cacheEvictionEvent) {
        try {

            if(cacheEvictionEvent.getEntityDefinitionId() != null){
                namedQueryOperationDefinitionCache.asMap().remove(cacheEvictionEvent.getEntityDefinitionId());
            }
        } catch (Exception e) {
            log.error("Failed to handle cache eviction (source: {})",
                      cacheEvictionEvent.getSource(), e);
        }
    }

    @Override
    public List<GqlOperationDefinition> getBuiltInOperationDefinitions() {
        return builtInOperationDefinitions;
    }

    @Override
    public List<GqlOperationDefinition> getNamedQueryOperationDefinitions(final EntityDefinition entityDefinition) {
        return namedQueryOperationDefinitionCache.get(entityDefinition.getId()).join();
    }

    private GraphQLFieldDefinition.Builder addPolicyIfPresent(GraphQLFieldDefinition.Builder builder,
                                                              List<EntityServiceDecorator> decorators){
        if(decorators != null){
            for(EntityServiceDecorator decorator : decorators){
                if(decorator instanceof PolicyDecorator policyDecorator){
                    builder = builder.withDirective(GqlUtils.policy(policyDecorator.getPolicies()));
                }
            }
        }
        return builder;
    }

}
