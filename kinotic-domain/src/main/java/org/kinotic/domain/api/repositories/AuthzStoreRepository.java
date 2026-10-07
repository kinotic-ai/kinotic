package org.kinotic.domain.api.repositories;

import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.internal.api.repositories.AbstractReconcilableRepository;
import org.kinotic.domain.internal.api.repositories.ReconcileStateRepository;
import org.kinotic.domain.internal.api.repositories.WatchEventRepository;
import org.kinotic.domain.internal.api.repositories.WatchedIndex;
import org.kinotic.domain.internal.api.repositories.WatchedStateRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch repository for {@link AuthzStore}s over the {@code kinotic_authz_store} index, stored by id
 * alone: an application's record id carries its organization, and the platform's store has none.
 */
@Component
public class AuthzStoreRepository extends AbstractReconcilableRepository<AuthzStore, AuthzModelRevision> {
    private static final WatchedIndex WATCHED = new WatchedIndex(WatchedType.AUTHZ_STORE, "kinotic_authz_store");

    public AuthzStoreRepository(CrudServiceTemplate crudServiceTemplate,
                                WatchedStateRepository watchedStateRepository,
                                WatchEventRepository watchEventRepository,
                                ReconcileStateRepository reconcileStateRepository) {
        super(WATCHED, AuthzStore.class, crudServiceTemplate, watchedStateRepository, watchEventRepository, reconcileStateRepository);
    }

    @Override
    public String scopeOf(AuthzStore record) {
        return record.getOrganizationId();
    }
}
