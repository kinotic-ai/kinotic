package org.kinotic.persistence.internal.endpoints.graphql;

import graphql.schema.GraphQLFieldDefinition;

import java.util.function.Function;

/**
 * Responsible for creating a {@link GraphQLFieldDefinition} from a {@link GqlFieldDefinitionData}
 * Created by Navíd Mitchell 🤪 on 6/13/24.
 */
public interface GqlFieldDefinitionFunction extends Function<GqlFieldDefinitionData, GraphQLFieldDefinition> {
}
