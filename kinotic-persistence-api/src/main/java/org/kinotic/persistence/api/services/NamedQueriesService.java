package org.kinotic.persistence.api.services;

import org.kinotic.continuum.api.annotations.Proxy;
import org.kinotic.continuum.api.annotations.Publish;
import org.kinotic.continuum.core.api.crud.IdentifiableCrudService;
import org.kinotic.continuum.core.api.crud.Page;
import org.kinotic.continuum.core.api.crud.Pageable;
import org.kinotic.continuum.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.domain.EntityContext;
import org.kinotic.persistence.api.domain.NamedQueriesDefinition;
import org.kinotic.persistence.api.domain.Structure;
import org.kinotic.persistence.api.domain.ParameterHolder;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/23/24.
 */
@Publish
@Proxy
public interface NamedQueriesService extends IdentifiableCrudService<NamedQueriesDefinition, String> {

    /**
     * Executes a named query.
     *
     * @param structure       the {@link Structure} that this named query is defined for
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param parameterHolder the parameters to pass to the query
     * @param type            the type of the entity
     * @param context         the context for this operation
     * @return {@link CompletableFuture} with the result of the query
     */
    <T> CompletableFuture<List<T>> executeNamedQuery(Structure structure,
                                                     String queryName,
                                                     ParameterHolder parameterHolder,
                                                     Class<T> type,
                                                     EntityContext context);

    /**
     * Executes a named query and returns a {@link Page} of results.
     *
     * @param structure       the {@link Structure} that this named query is defined for
     * @param queryName       the name of {@link FunctionDefinition} that defines the query
     * @param parameterHolder the parameters to pass to the query
     * @param pageable        the page settings to be used
     * @param type            the type of the entity
     * @param context         the context for this operation
     * @return {@link CompletableFuture} with the result of the query
     */
    <T> CompletableFuture<Page<T>> executeNamedQueryPage(Structure structure,
                                                         String queryName,
                                                         ParameterHolder parameterHolder,
                                                         Pageable pageable,
                                                         Class<T> type,
                                                         EntityContext context);

    /**
     * Finds all {@link NamedQueriesDefinition} for a given application and structure.
     * @param applicationId the id of the application that the structure belongs to
     * @param structure the name of the structure that this {@link NamedQueriesDefinition} is defined for
     * @return {@link CompletableFuture} with the {@link NamedQueriesDefinition} or null if not found
     */
    CompletableFuture<NamedQueriesDefinition> findByApplicationAndStructure(String applicationId, String structure);

    /**
     * This operation makes all the recent writes immediately available for search.
     * @return a future that will complete when the index has been synced
     */
    CompletableFuture<Void> syncIndex();


}
