package org.kinotic.persistence.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.ParameterHolder;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/23/24.
 */
@Publish
public interface NamedQueriesService {

    /**
     * Executes a named query.
     *
     * @param entityDefinition       the {@link EntityDefinition} that this named query is defined for
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param parameterHolder the parameters to pass to the query
     * @param type            the type of the entity
     * @param context         the context for this operation
     * @return {@link CompletableFuture} with the result of the query
     */
    <T> CompletableFuture<List<T>> executeNamedQuery(EntityDefinition entityDefinition,
                                                     String queryName,
                                                     ParameterHolder parameterHolder,
                                                     Class<T> type,
                                                     EntityContext context);

    /**
     * Executes a named query and returns a {@link Page} of results.
     *
     * @param entityDefinition       the {@link EntityDefinition} that this named query is defined for
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param parameterHolder the parameters to pass to the query
     * @param pageable        the page settings to be used
     * @param type            the type of the entity
     * @param context         the context for this operation
     * @return {@link CompletableFuture} with the result of the query
     */
    <T> CompletableFuture<Page<T>> executeNamedQueryPage(EntityDefinition entityDefinition,
                                                         String queryName,
                                                         ParameterHolder parameterHolder,
                                                         Pageable pageable,
                                                         Class<T> type,
                                                         EntityContext context);

}
