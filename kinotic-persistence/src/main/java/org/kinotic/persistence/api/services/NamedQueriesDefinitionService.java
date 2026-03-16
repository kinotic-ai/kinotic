package org.kinotic.persistence.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.NamedQueriesDefinition;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪on 4/23/24.
 */
@Publish
public interface NamedQueriesDefinitionService extends IdentifiableCrudService<NamedQueriesDefinition, String> {

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
