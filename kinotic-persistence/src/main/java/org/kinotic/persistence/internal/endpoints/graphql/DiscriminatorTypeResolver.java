package org.kinotic.persistence.internal.endpoints.graphql;

import graphql.TypeResolutionEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.TypeResolver;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * This class is used to resolve the type of field based on a discriminator field.
 * Created by Navíd Mitchell 🤪 on 6/18/24.
 */
@RequiredArgsConstructor
public class DiscriminatorTypeResolver implements TypeResolver {
    private final String discriminatorField;

    @Override
    public GraphQLObjectType getType(TypeResolutionEnvironment env) {
        Object obj = env.getObject();
        if (!(obj instanceof Map)) {
            throw new IllegalStateException("DiscriminatorTypeResolver can only be used with Map objects");
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) obj;
        Object discriminatorValue = map.get(discriminatorField);
        if(discriminatorValue instanceof String){
            String typeName = discriminatorValue.toString();
            return env.getSchema().getObjectType(typeName);
        } else {
            throw new IllegalStateException("Discriminator field must be a string");
        }
    }
}
