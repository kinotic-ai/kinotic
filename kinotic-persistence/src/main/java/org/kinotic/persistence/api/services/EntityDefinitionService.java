
package org.kinotic.persistence.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.ProjectScopedCrudService;
import org.kinotic.persistence.api.model.EntityDefinition;

import java.util.concurrent.CompletableFuture;

@Publish
public interface EntityDefinitionService extends ProjectScopedCrudService<EntityDefinition, String> {

    CompletableFuture<EntityDefinition> create(EntityDefinition entityDefinition);

    CompletableFuture<Page<EntityDefinition>> findAllPublishedForApplication(String applicationId, Pageable pageable);

    CompletableFuture<Void> publish(String entityDefinitionId);

    CompletableFuture<Void> syncIndex();

    CompletableFuture<Void> unPublish(String entityDefinitionId);

    CompletableFuture<EntityDefinition> saveSync(EntityDefinition entity);

}
