package org.kinotic.persistence.internal.endpoints.graphql;

import com.apollographql.federation.graphqljava.Federation;
import com.apollographql.federation.graphqljava.printer.ServiceSDLPrinter;
import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import graphql.GraphQL;
import graphql.language.OperationDefinition;
import graphql.schema.*;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.vertx.ext.web.handler.graphql.GraphQLHandler;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.WordUtils;
import org.kinotic.continuum.api.crud.Pageable;
import org.kinotic.continuum.idl.api.converter.IdlConverter;
import org.kinotic.persistence.api.domain.Structure;
import org.kinotic.persistence.api.domain.idl.decorators.EntityServiceDecorator;
import org.kinotic.persistence.api.domain.idl.decorators.EntityServiceDecoratorsDecorator;
import org.kinotic.persistence.api.services.EntitiesService;
import org.kinotic.auth.internal.services.DefaultCaffeineCacheFactory;
import org.kinotic.persistence.internal.api.services.StructureConversionService;
import org.kinotic.persistence.internal.api.services.StructureDAO;
import org.kinotic.persistence.api.domain.EntityOperation;
import org.kinotic.persistence.internal.endpoints.graphql.datafetchers.EntitiesDataFetcher;
import org.kinotic.persistence.internal.endpoints.graphql.datafetchers.EntitiesTypeResolver;
import org.kinotic.persistence.internal.idl.converters.graphql.GqlConversionState;
import org.kinotic.persistence.internal.idl.converters.graphql.GqlTypeHolder;
import org.kinotic.persistence.internal.utils.GqlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static graphql.Scalars.GraphQLInt;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLEnumType.newEnum;
import static graphql.schema.GraphQLInputObjectField.newInputObjectField;
import static graphql.schema.GraphQLInputObjectType.newInputObject;
import static graphql.schema.GraphQLNonNull.nonNull;
import static graphql.schema.GraphQLObjectType.newObject;

/**
 * Created by Navíd Mitchell 🤪on 6/25/23.
 */
@Component
@RequiredArgsConstructor
public class GqlSchemaHandlerCacheLoader implements AsyncCacheLoader<String, GraphQLHandler> {

    private static final Logger log = LoggerFactory.getLogger(GqlSchemaHandlerCacheLoader.class);
    private static final EntitiesTypeResolver ENTITIES_TYPE_RESOLVER = new EntitiesTypeResolver();

    // Versions supported by the Apollo router are defined here https://www.apollographql.com/docs/graphos/reference/federation/versions
    private static final String FEDERATION_BASE = """
        extend schema
            @link(
                url: "https://specs.apollo.dev/federation/v2.6"
                import: [
                    "@key",
                    "@policy"
                ]
            )
        
        directive @entity(multiTenancyType: MultiTenancyType = NONE) on OBJECT

        enum MultiTenancyType {
            NONE
            SHARED
        }

        directive @id on FIELD_DEFINITION
        
        """;

    private final DefaultCaffeineCacheFactory cacheFactory;
    private final EntitiesService entitiesService;
    private final StructureDAO structureDAO;
    private final StructureConversionService structureConversionService;
    private final GqlOperationDefinitionService gqlOperationDefinitionService;

    @Override
    public CompletableFuture<GraphQLHandler> asyncLoad(String key, Executor executor) {
        long now = System.nanoTime();
        return createGraphQlSchema(key, executor)
                .thenCompose(schema -> {

                    GraphQL.Builder builder = GraphQL.newGraphQL(schema)
                                                     .preparsedDocumentProvider(new CachingPreparsedDocumentProvider(cacheFactory));
                    GraphQL graphQL = builder.build();

                    log.debug("Finished creating GraphQL Schema for application: {} in {}ms",
                              key,
                              TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - now));

                    return CompletableFuture.completedFuture(GraphQLHandler.create(graphQL));
                });
    }

    @WithSpan
    private CompletableFuture<GraphQLSchema> createGraphQlSchema(@SpanAttribute("applicationId")
                                                                 String applicationId,
                                                                 Executor executor) {
        return structureDAO
                .findAllPublishedForApplication(applicationId, Pageable.ofSize(500))
                .thenComposeAsync(structuresPage -> {
                    if(structuresPage.getTotalElements() > 0) {
                        log.debug("Creating GraphQL Schema for application: {}", applicationId);

                        // Create the pageable types that are used in all queries
                        GraphQLInputObjectType sortType = createSortType();
                        GraphQLTypeReference sortReference = new GraphQLTypeReference(sortType.getName());

                        GraphQLInputObjectType pageableType = createPageableType(sortReference);
                        GraphQLTypeReference pageableReference = new GraphQLTypeReference(pageableType.getName());

                        GraphQLInputObjectType cursorPageableType = createCursorPageableType(sortReference);
                        GraphQLTypeReference cursorPageableReference = new GraphQLTypeReference(cursorPageableType.getName());

                        // Create the query and mutation builders so, we can add operations to them
                        GraphQLObjectType.Builder queryBuilder = newObject().name("Query");
                        GraphQLObjectType.Builder mutationBuilder = newObject().name("Mutation");
                        GraphQLCodeRegistry.Builder codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry();

                        IdlConverter<GqlTypeHolder, GqlConversionState> converter
                                = structureConversionService.createGqlConverter();

                        for (Structure structure : structuresPage.getContent()) {

                            GqlTypeHolder structureType = converter.convert(structure.getEntityDefinition());
                            GraphQLObjectType outputType;
                            if (structureType.outputType() instanceof GraphQLObjectType) {
                                outputType = (GraphQLObjectType) structureType.outputType();
                            } else {
                                throw new IllegalStateException("Output type must be a GraphQLObjectType");
                            }

                            GraphQLInputObjectType inputType = getGraphQLInputObjectType(structureType);

                            GraphQLTypeReference graphQLTypeReference = new GraphQLTypeReference(outputType.getName());
                            GraphQLNamedOutputType pageResponseType = GqlUtils.wrapTypeWithPage(graphQLTypeReference);
                            GraphQLNamedOutputType cursorPageResponseType = GqlUtils.wrapTypeWithCursorPage(graphQLTypeReference);


                            EntityServiceDecoratorsDecorator esdDecorator = structure.getEntityDefinition()
                                                                                     .findDecorator(EntityServiceDecoratorsDecorator.class);
                            Map<EntityOperation, List<EntityServiceDecorator>> entityOperationsMap = Map.of();
                            if(esdDecorator != null){
                                entityOperationsMap = esdDecorator.getConfig().getOperationDecoratorMap();
                            }

                            String structureName = WordUtils.capitalize(structure.getName());
                            GqlFieldDefinitionData fieldDefinitionData
                                    = GqlFieldDefinitionData.builder()
                                                            .converter(converter)
                                                            .inputType(inputType)
                                                            .outputType(outputType)
                                                            .offsetPageableReference(pageableReference)
                                                            .cursorPageableReference(cursorPageableReference)
                                                            .pageResponseType(pageResponseType)
                                                            .cursorPageResponseType(cursorPageResponseType)
                                                            .structureName(structureName)
                                                            .entityOperationsMap(entityOperationsMap)
                                                            .build();

                            // Add all graphQL operations to the schema
                            addOperations(fieldDefinitionData, codeRegistryBuilder, mutationBuilder, queryBuilder, structure);
                        }

                        // Add all type resolvers to the schema
                        for (Pair<GraphQLUnionType, TypeResolver> pair : converter.getConversionContext()
                                                                                  .state()
                                                                                  .getUnionTypes()
                                                                                  .values()) {
                            codeRegistryBuilder.typeResolver(pair.getLeft(), pair.getRight());
                        }

                        // Add all additional types to the schema
                        Map<String, GraphQLType> additionalTypes = new HashMap<>(converter.getConversionContext()
                                                                                          .state()
                                                                                          .getReferencedTypes());

                        // Since the transformer does not add the necessary directives if not loaded from a file
                        // we load an empty file first then uss that as the base for our schema
                        GraphQLSchema federationBaseSchema = Federation.transform(FEDERATION_BASE)
                                                                       .build();

                        GraphQLSchema.Builder graphQLSchemaBuilder = GraphQLSchema.newSchema(federationBaseSchema)
                                                                                  .codeRegistry(codeRegistryBuilder.build())
                                                                                  .additionalType(sortType)
                                                                                  .additionalType(pageableType)
                                                                                  .additionalType(cursorPageableType);

                        GraphQLObjectType query = queryBuilder.build();
                        if (!query.getFieldDefinitions().isEmpty()) {
                            graphQLSchemaBuilder.query(query);
                        }

                        GraphQLObjectType mutation = mutationBuilder.build();
                        if (!mutation.getFieldDefinitions().isEmpty()) {
                            graphQLSchemaBuilder.mutation(mutation);
                        }

                        graphQLSchemaBuilder.additionalTypes(Set.copyOf(additionalTypes.values()));

                        GraphQLSchema graphQLSchema = graphQLSchemaBuilder.build();
                        graphQLSchema = Federation.transform(graphQLSchema)
                                                  .setFederation2(true)
                                                  .fetchEntities(new EntitiesDataFetcher(entitiesService, applicationId))
                                                  .resolveEntityType(ENTITIES_TYPE_RESOLVER)
                                                  .build();

                        if (log.isTraceEnabled()) {
                            log.trace("GraphQL Schema for application {}\n{}",
                                      applicationId,
                                      ServiceSDLPrinter.generateServiceSDLV2(graphQLSchema));
                        }

                        return CompletableFuture.completedFuture(graphQLSchema);
                    }else{
                        return CompletableFuture.failedFuture(new IllegalArgumentException("No published structures found for application: " + applicationId));
                    }
                }, executor);
    }

    /**
     * Adds all operations to the query and mutation builders
     * @param fieldDefinitionData the data needed to build the operations
     * @param mutationBuilder the mutation builder
     * @param queryBuilder the query builder
     */
    private void addOperations(GqlFieldDefinitionData fieldDefinitionData,
                               GraphQLCodeRegistry.Builder codeRegistryBuilder,
                               GraphQLObjectType.Builder mutationBuilder,
                               GraphQLObjectType.Builder queryBuilder,
                               Structure structure) {

        // Add built in operations
        for (GqlOperationDefinition definition : gqlOperationDefinitionService.getBuiltInOperationDefinitions()) {
            addOperation(definition, fieldDefinitionData, codeRegistryBuilder, mutationBuilder, queryBuilder, structure);
        }

        // Add named query operations
        for(GqlOperationDefinition definition : gqlOperationDefinitionService.getNamedQueryOperationDefinitions(structure)){
            addOperation(definition, fieldDefinitionData, codeRegistryBuilder, mutationBuilder, queryBuilder, structure);
        }
    }

    private static void addOperation(GqlOperationDefinition definition,
                                     GqlFieldDefinitionData fieldDefinitionData,
                                     GraphQLCodeRegistry.Builder codeRegistryBuilder,
                                     GraphQLObjectType.Builder mutationBuilder,
                                     GraphQLObjectType.Builder queryBuilder,
                                     Structure structure) {

        Function<GqlFieldDefinitionData, GraphQLFieldDefinition> function = definition.getFieldDefinitionFunction();

        if (definition.getOperationType() == OperationDefinition.Operation.QUERY) {

            GraphQLFieldDefinition queryFieldDefinition = function.apply(fieldDefinitionData);

            queryBuilder.field(queryFieldDefinition);

            codeRegistryBuilder.dataFetcher(FieldCoordinates.coordinates("Query", queryFieldDefinition.getName()),
                                            definition.getDataFetcherDefinitionFunction().apply(structure));

        } else if (definition.getOperationType() == OperationDefinition.Operation.MUTATION) {

            if (fieldDefinitionData.getInputType() != null) {

                GraphQLFieldDefinition mutationFieldDefinition = function.apply(fieldDefinitionData);

                mutationBuilder.field(mutationFieldDefinition);

                codeRegistryBuilder.dataFetcher(FieldCoordinates.coordinates("Mutation", mutationFieldDefinition.getName()),
                                                definition.getDataFetcherDefinitionFunction().apply(structure));
            }

        } else {
            log.error("Unsupported operation {} type: {}",
                      definition.getOperationName(),
                      definition.getOperationType());
        }
    }

    private static GraphQLInputObjectType getGraphQLInputObjectType(GqlTypeHolder gqlTypeHolder) {
        GraphQLInputObjectType inputType = null;
        // Will be null if a UnionC3Type is found anywhere in the object graph
        if (gqlTypeHolder.inputType() != null) {
            if (gqlTypeHolder.inputType() instanceof GraphQLInputObjectType) {
                inputType = (GraphQLInputObjectType) gqlTypeHolder.inputType();
            } else {
                throw new IllegalStateException("Input type must be a GraphQLInputObjectType");
            }
        }
        return inputType;
    }

    private static GraphQLInputObjectType createOrderType() {
        GraphQLEnumType enumType = newEnum().name("Direction")
                                            .value("ASC")
                                            .value("DESC")
                                            .build();

        GraphQLInputObjectType.Builder inputBuilder = newInputObject().name("Order");

        inputBuilder.field(newInputObjectField()
                                   .name("direction")
                                   .type(nonNull(enumType)))
                    .field(newInputObjectField()
                                   .name("property")
                                   .type(nonNull(GraphQLString)));

        return inputBuilder.build();
    }

    private static GraphQLInputObjectType createSortType() {
        GraphQLInputObjectType.Builder inputBuilder = newInputObject().name("Sort");
        inputBuilder.field(newInputObjectField()
                                   .name("orders")
                                   .type(nonNull(GraphQLList.list(nonNull(createOrderType())))));
        return inputBuilder.build();
    }

    private static GraphQLInputObjectType createPageableType(GraphQLTypeReference sortType) {
        GraphQLInputObjectType.Builder inputBuilder = newInputObject().name("Pageable");
        inputBuilder.field(newInputObjectField()
                                   .name("pageNumber")
                                   .type(nonNull(GraphQLInt)))
                    .field(newInputObjectField()
                                   .name("pageSize")
                                   .type(nonNull(GraphQLInt)))
                    .field(newInputObjectField()
                                   .name("sort")
                                   .type(sortType));
        return inputBuilder.build();
    }

    private static GraphQLInputObjectType createCursorPageableType(GraphQLTypeReference sortType) {
        GraphQLInputObjectType.Builder inputBuilder = newInputObject().name("CursorPageable");
        inputBuilder.field(newInputObjectField()
                                   .name("cursor")
                                   .description("The cursor to start from or null to start from the beginning")
                                   .type(GraphQLString))
                    .field(newInputObjectField()
                                   .name("pageSize")
                                   .description("The number of elements to return")
                                   .type(nonNull(GraphQLInt)))
                    .field(newInputObjectField()
                                   .name("sort")
                                   .description("Sort criteria for the query")
                                   .type(sortType));
        return inputBuilder.build();
    }

}
