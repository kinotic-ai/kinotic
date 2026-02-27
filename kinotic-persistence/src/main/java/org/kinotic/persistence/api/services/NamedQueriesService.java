package org.kinotic.persistence.api.services;

import org.kinotic.domain.api.services.crud.IdentifiableCrudService;
import org.kinotic.domain.api.services.crud.Page;
import org.kinotic.domain.api.services.crud.Pageable;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.api.model.NamedQueriesDefinition;
import org.kinotic.persistence.api.model.ParameterHolder;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.core.api.annotations.Publish;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/23/24.
 */
@Publish
public interface NamedQueriesService extends IdentifiableCrudService<NamedQueriesDefinition, String> {

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

    /**
     * Finds all {@link NamedQueriesDefinition} for a given application and {@link EntityDefinition}.
     * @param applicationId the id of the application that the {@link EntityDefinition} belongs to
     * @param entityDefinitionName the name of the {@link EntityDefinition} that this {@link NamedQueriesDefinition} is defined for
     * @return {@link CompletableFuture} with the {@link NamedQueriesDefinition} or null if not found
     */
    CompletableFuture<NamedQueriesDefinition> findByApplicationAndEntityDefinition(String applicationId, String entityDefinitionName);

    /**
     * This operation makes all the recent writes immediately available for search.
     * @return a future that will complete when the index has been synced
     */
    CompletableFuture<Void> syncIndex();


}
