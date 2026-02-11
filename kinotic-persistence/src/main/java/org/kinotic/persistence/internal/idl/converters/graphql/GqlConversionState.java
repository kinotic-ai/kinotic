package org.kinotic.persistence.internal.idl.converters.graphql;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.kinotic.persistence.api.config.StructuresProperties;
import org.kinotic.persistence.internal.idl.converters.common.BaseConversionState;

import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import graphql.schema.TypeResolver;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Created by Navíd Mitchell 🤪 on 5/14/23.
 */
@Getter
@Setter
@Accessors(chain = true)
public class GqlConversionState extends BaseConversionState {

    private final Map<String, GraphQLType> referencedTypes = new HashMap<>();

    /**
     * Union types keyed by name with the value being a pair of the GraphQLUnionType and the TypeResolver
     */
    private Map<String, Pair<GraphQLUnionType, TypeResolver>> unionTypes = new HashMap<>();

    public GqlConversionState(StructuresProperties structuresProperties) {
        super(structuresProperties);
    }
}
