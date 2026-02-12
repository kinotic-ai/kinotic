package org.kinotic.persistence.internal.endpoints.graphql;

import graphql.schema.*;
import lombok.Builder;
import lombok.Getter;
import org.kinotic.continuum.api.crud.CursorPage;
import org.kinotic.continuum.api.crud.Page;
import org.kinotic.continuum.api.crud.Pageable;
import org.kinotic.continuum.idl.api.converter.IdlConverter;
import org.kinotic.continuum.idl.api.schema.ObjectC3Type;
import org.kinotic.persistence.api.domain.Structure;
import org.kinotic.persistence.api.domain.idl.decorators.EntityServiceDecorator;
import org.kinotic.persistence.api.domain.idl.decorators.EntityServiceDecoratorsDecorator;
import org.kinotic.persistence.api.domain.EntityOperation;
import org.kinotic.persistence.internal.idl.converters.graphql.GqlConversionState;
import org.kinotic.persistence.internal.idl.converters.graphql.GqlTypeHolder;

import java.util.List;
import java.util.Map;

/**
 * Created by Navíd Mitchell 🤪 on 12/14/23.
 */
@Builder
@Getter
public class GqlFieldDefinitionData {

    /**
     * The {@link IdlConverter} currently being used to convert the {@link Structure} types to {@link GraphQLType}s
     */
    private final IdlConverter<GqlTypeHolder, GqlConversionState> converter;

    /**
     * The {@link GraphQLNamedOutputType} for the {@link CursorPage} type containing the {@link ObjectC3Type} for the {@link Structure}
     */
    private final GraphQLNamedOutputType cursorPageResponseType;

    /**
     * The {@link GraphQLTypeReference} for the {@link Pageable} type used for all requests needing paging
     */
    private final GraphQLTypeReference cursorPageableReference;

    /**
     * The {@link EntityServiceDecorator} for each operation if provided by the {@link EntityServiceDecoratorsDecorator} for the {@link Structure#getEntityDefinition()} or an empty map
     */
    private final Map<EntityOperation, List<EntityServiceDecorator>> entityOperationsMap;

    /**s
     * The {@link GraphQLInputObjectType} that is created from the {@link ObjectC3Type} for the {@link Structure} or null if the {@link Structure}s does not have an input type
     */
    private final GraphQLInputObjectType inputType;

    /**
     * The {@link GraphQLTypeReference} for the {@link Pageable} type used for all requests needing paging
     */
    private final GraphQLTypeReference offsetPageableReference;

    /**
     * The {@link GraphQLObjectType} that is created from the {@link ObjectC3Type} for the {@link Structure}
     */
    private final GraphQLObjectType outputType;

    /**
     * The {@link GraphQLNamedOutputType} for the {@link Page} type containing the {@link ObjectC3Type} for the {@link Structure}
     */
    private final GraphQLNamedOutputType pageResponseType;

    /**
     * The {@link Structure#getName()} (first letter capitalized) that the {@link GraphQLFieldDefinition} is for
     */
    private final String structureName;

}
