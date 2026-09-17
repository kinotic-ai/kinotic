package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import org.kinotic.management.api.model.UiDeployment;
import org.kinotic.management.api.repositories.UiDeploymentRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory stand-in for the Elasticsearch backed {@link UiDeploymentRepository}; what is
 * saved is read back from {@link #saved}.
 */
public class StubUiDeploymentRepository extends UiDeploymentRepository {

    public final Map<String, UiDeployment> saved = new LinkedHashMap<>();

    public StubUiDeploymentRepository() {
        super(null);
    }

    @Override
    public Future<UiDeployment> findById(String id) {
        return Future.succeededFuture(saved.get(id));
    }

    @Override
    public Future<UiDeployment> save(UiDeployment value) {
        saved.put(value.getId(), value);
        return Future.succeededFuture(value);
    }

    @Override
    public Future<UiDeployment> saveSync(UiDeployment value) {
        return save(value);
    }

    @Override
    public Future<List<UiDeployment>> findAllForProject(String projectId) {
        return Future.succeededFuture(saved.values().stream().filter(row -> projectId.equals(row.getProjectId())).toList());
    }

}
