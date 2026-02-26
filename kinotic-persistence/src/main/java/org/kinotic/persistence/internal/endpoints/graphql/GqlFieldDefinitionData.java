package org.kinotic.persistence.internal.endpoints.graphql;

import graphql.schema.*;
import lombok.Builder;
import lombok.Getter;
import org.kinotic.domain.api.services.crud.CursorPage;
import org.kinotic.domain.api.services.crud.Page;
import org.kinotic.domain.api.services.crud.Pageable;
import org.kinotic.idl.api.converter.IdlConverter;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.persistence.api.model.Structure;
import org.kinotic.persistence.api.model.idl.decorators.EntityServiceDecorator;
import org.kinotic.persistence.api.model.idl.decorators.EntityServiceDecoratorsDecorator;
import org.kinotic.persistence.api.model.EntityOperation;
import org.kinotic.persistence.internal.converters.graphql.GqlConversionState;
import org.kinotic.persistence.internal.converters.graphql.GqlTypeHolder;

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
