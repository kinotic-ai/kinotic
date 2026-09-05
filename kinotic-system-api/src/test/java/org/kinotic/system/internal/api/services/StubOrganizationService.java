package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.services.OrganizationService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory stand-in for the Elasticsearch backed {@link OrganizationService}: what the
 * provisioners save is read back from {@link #saved}.
 */
public class StubOrganizationService implements OrganizationService {

    public final Map<String, Organization> saved = new LinkedHashMap<>();

    @Override
    public Future<Organization> save(Organization entity) {
        saved.put(entity.getId(), entity);
        return Future.succeededFuture(entity);
    }

    @Override
    public Future<Organization> saveSync(Organization entity) {
        return save(entity);
    }

    @Override
    public Future<Organization> create(Organization entity) {
        return save(entity);
    }

    @Override
    public Future<Organization> createSync(Organization entity) {
        return save(entity);
    }

    @Override
    public Future<Organization> findById(String id) {
        return Future.succeededFuture(saved.get(id));
    }

    @Override
    public Future<Long> count() {
        return Future.succeededFuture((long) saved.size());
    }

    @Override
    public Future<Void> deleteById(String id) {
        saved.remove(id);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> deleteByIdSync(String id) {
        return deleteById(id);
    }

    @Override
    public Future<Page<Organization>> findAll(Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Page<Organization>> search(String searchText, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<Void> syncIndex() {
        return Future.succeededFuture();
    }

    @Override
    public Future<Organization> provision(String organizationId) {
        throw new UnsupportedOperationException();
    }

}
