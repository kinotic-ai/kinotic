package org.kinotic.structures.internal.endpoints.graphql;

import org.kinotic.structures.api.domain.Structure;

import java.util.List;

/**
 * This service provides all the {@link GqlOperationDefinition} available
 * Created by Navíd Mitchell 🤪 on 12/14/23.
 */
public interface GqlOperationDefinitionService {


    /**
     * Returns the built-in operations that are always available, such as findById, findAll, etc...
     * @return all the {@link GqlOperationDefinition} available
     */
    List<GqlOperationDefinition> getBuiltInOperationDefinitions();

    /**
     * Returns all the named query operations that are available for the given {@link Structure}
     * @param structure to get the named query operations for
     * @return all the {@link GqlOperationDefinition} available for the given {@link Structure}
     */
    List<GqlOperationDefinition> getNamedQueryOperationDefinitions(Structure structure);

}
