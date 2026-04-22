package org.kinotic.core.api.crud;

import java.util.concurrent.CompletableFuture;

public interface ApplicationScopedCrudService<T extends Identifiable<ID>, ID> extends IdentifiableCrudService<T, ID> {

    CompletableFuture<Long> countForApplication(String applicationId);

    CompletableFuture<Page<T>> findAllForApplication(String applicationId, Pageable pageable);

}
