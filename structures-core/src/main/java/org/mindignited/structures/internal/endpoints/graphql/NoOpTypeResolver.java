package org.mindignited.structures.internal.endpoints.graphql;

import graphql.TypeResolutionEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.TypeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A no-op type resolver that always returns null.
 * And Type Resolvers are required to be defined for interfaces and unions.
 * Created by Navíd Mitchell 🤪 on 2/4/24.
 */
public class NoOpTypeResolver implements TypeResolver {
    private static final Logger log = LoggerFactory.getLogger(NoOpTypeResolver.class);
    @Override
    public GraphQLObjectType getType(TypeResolutionEnvironment env) {
        log.warn("NoOpTypeResolver called for field {}, no type will be returned.\nThis usually happens if something is misconfigured." +
                         "\nCheck to make sure all interfaces and unions have a valid @Discriminator's defined.", env.getField());
        return null;
    }
}
