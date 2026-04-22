package org.kinotic.core.api.crud;

import java.util.concurrent.CompletableFuture;

public interface ProjectScopedCrudService<T extends Identifiable<ID>, ID> extends ApplicationScopedCrudService<T, ID> {

    CompletableFuture<Long> countForProject(String projectId);

    CompletableFuture<Page<T>> findAllForProject(String projectId, Pageable pageable);

}
