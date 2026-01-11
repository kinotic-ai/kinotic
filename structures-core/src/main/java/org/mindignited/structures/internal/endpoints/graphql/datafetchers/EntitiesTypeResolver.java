package org.mindignited.structures.internal.endpoints.graphql.datafetchers;

import graphql.TypeResolutionEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.TypeResolver;

/**
 * Created by Navíd Mitchell 🤪 on 6/18/24.
 */
public class EntitiesTypeResolver implements TypeResolver {

    @Override
    public GraphQLObjectType getType(TypeResolutionEnvironment env) {
        if(env.getObject() instanceof EntityMap){
            return env.getSchema().getObjectType(((EntityMap) env.getObject()).getTypeName());
        }else{
            throw new IllegalStateException("EntitiesTypeResolver can only be used with EntityMap objects");
        }
    }
}
