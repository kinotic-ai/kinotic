package org.kinotic.domain.api.repositories;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.CursorPage;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.internal.api.repositories.AbstractReconcilableRepository;
import org.kinotic.domain.internal.api.repositories.ReconcileStateRepository;
import org.kinotic.domain.internal.api.repositories.WatchedDocument;
import org.kinotic.domain.internal.api.repositories.WatchEventRepository;
import org.kinotic.domain.internal.api.repositories.WatchedIndex;
import org.kinotic.domain.internal.api.repositories.WatchedStateRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch repository for {@link AuthzStore}s over the {@code kinotic_authz_store} index, stored by id
 * alone: an application's record id carries its organization, and the system's store has none.
 */
@Component
public class AuthzStoreRepository extends AbstractReconcilableRepository<AuthzStore, AuthzModelRevision> {
    private static final int PAGE_SIZE = 500;
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

    /**
     * @see ReconcileStateRepository#renewDesired(WatchedDocument, String)
     */
    public Future<Void> renewDesired(String id, String source) {
        return reconcileStateRepository.renewDesired(document(id), source).mapEmpty();
    }

    /**
     * Renews the intent of every store, the system's and each application's.
     *
     * @see ReconcileStateRepository#renewDesired(WatchedDocument, String)
     */
    public Future<Void> renewAllDesired(String source) {
        return renewAllDesired(source, null);
    }

    private Future<Void> renewAllDesired(String source, String cursor) {
        return findAll(Pageable.create(cursor, PAGE_SIZE, Sort.by("id")),
                       b -> b.source(sc -> sc.filter(f -> f.includes("id"))))
                .compose(page -> {
                    Future<Void> ret = Future.succeededFuture();
                    for (AuthzStore store : page.getContent()) {
                        ret = ret.compose(v -> renewDesired(store.getId(), source));
                    }
                    String next = page instanceof CursorPage<AuthzStore> cursorPage ? cursorPage.getCursor() : null;
                    return page.getContent().size() < PAGE_SIZE || next == null
                            ? ret
                            : ret.compose(v -> renewAllDesired(source, next));
                });
    }
}
