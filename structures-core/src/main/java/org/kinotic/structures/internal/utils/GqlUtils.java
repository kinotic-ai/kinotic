package org.kinotic.structures.internal.utils;

import tools.jackson.databind.ObjectMapper;
import graphql.schema.*;

import java.util.List;
import java.util.Map;

import static graphql.Scalars.GraphQLInt;
import static graphql.Scalars.GraphQLString;
import static graphql.introspection.Introspection.DirectiveLocation.*;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLDirective.newDirective;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLNonNull.nonNull;
import static graphql.schema.GraphQLObjectType.newObject;

/**
 * Created by Navíd Mitchell 🤪 on 6/13/24.
 */
public class GqlUtils {

    public static final GraphQLTypeReference federationPolicyTypeRef = new GraphQLTypeReference("federation__Policy");

    private static final GraphQLList nestedPolicyList = GraphQLList.list( // Define the [[federation__Policy!]!] input type
                                                                   new GraphQLNonNull(
                                                                           GraphQLList.list(
                                                                                   new GraphQLNonNull(federationPolicyTypeRef)
                                                                           )
                                                                   )
    );

    private static final GraphQLArgument policiesArgument = newArgument()
            .name("policies")
            .description("Nested list of federation__Policy")
            .type(new GraphQLNonNull(nestedPolicyList))
            .build();

    private static final GraphQLDirective policyDirective = newDirective()
            .name("policy")
            .description("Apollo federation policy directive")
            .argument(policiesArgument)
            .validLocations(
                    OBJECT,
                    FIELD_DEFINITION,
                    INTERFACE,
                    SCALAR,
                    ENUM
            )
            .build();

    /**
     * Creates a {@link GraphQLDirective} for the Apollo @policy directive
     * @param policies a list of policies to apply
     * @return the directive
     */
    public static GraphQLDirective policy(List<List<String>> policies){
        return newDirective(policyDirective)
                .argument(policiesArgument(policies))
                .build();
    }

    private static GraphQLArgument policiesArgument(List<List<String>> policies){
        return newArgument(policiesArgument)
                .value(policies)
                .build();
    }

    public static <T> T parseVariable(Map<String, Object> variables,
                                      String variableName,
                                      Class<T> clazz,
                                      ObjectMapper objectMapper) {
        Object value = variables.get(variableName);
        if (value != null) {
            return objectMapper.convertValue(value, clazz);
        } else {
            throw new IllegalArgumentException("Variable: " + variableName + " not supplied");
        }
    }

    public static GraphQLNamedOutputType wrapTypeWithPage(GraphQLNamedOutputType namedOutputType) {
        return newObject()
                .name(namedOutputType.getName() + "Page")
                .field(newFieldDefinition()
                               .name("totalElements")
                               .type(GraphQLInt))
                .field(newFieldDefinition()
                               .name("content")
                               .type(nonNull(GraphQLList.list(nonNull(namedOutputType)))))
                .build();
    }

    public static GraphQLNamedOutputType wrapTypeWithCursorPage(GraphQLNamedOutputType namedOutputType) {
        return newObject()
                .name(namedOutputType.getName() + "CursorPage")
                .field(newFieldDefinition()
                               .name("totalElements")
                               .type(GraphQLInt))
                .field(newFieldDefinition()
                               .name("cursor")
                               .type(GraphQLString))
                .field(newFieldDefinition()
                               .name("content")
                               .type(nonNull(GraphQLList.list(nonNull(namedOutputType)))))
                .build();
    }
}
