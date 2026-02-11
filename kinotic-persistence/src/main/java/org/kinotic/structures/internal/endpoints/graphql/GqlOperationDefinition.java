package org.kinotic.structures.internal.endpoints.graphql;

import graphql.language.OperationDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLFieldDefinition;
import lombok.Builder;
import lombok.Getter;
import org.kinotic.structures.api.domain.Structure;

import java.util.function.Function;

/**
 * Holds all the information needed to define a GraphQL operation and how to execute it
 * Created by Navíd Mitchell 🤪 on 12/14/23.
 */
@Builder
@Getter
public class GqlOperationDefinition {

    /**
     * The operation name, or in the case of built-in operations, the prefix of the operation name
     * Such as findById, findAll, update, delete, etc...
     * NOTE: This must be unique for the operation, and only contain alpha characters.
     */
    private final String operationName;

    /**
     * The type of operation this definition is for
     */
    private final OperationDefinition.Operation operationType;

    /**
     * The function that will define the {@link GraphQLFieldDefinition} for this operation
     */
    private final GqlFieldDefinitionFunction fieldDefinitionFunction;

    /**
     * The function that will define the {@link DataFetcher} for this operation
     */
    private final Function<Structure, DataFetcher<?>> dataFetcherDefinitionFunction;

}
