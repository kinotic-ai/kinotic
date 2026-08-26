package org.kinotic.system.internal.api.deployment;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.ProjectDeployment;
import org.kinotic.domain.internal.api.repositories.ProjectDeploymentRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory {@link ProjectDeploymentRepository}: rows are held by id, so tests can seed an
 * existing deployment and inspect what a run recorded.
 */
public class FakeProjectDeploymentRepository extends ProjectDeploymentRepository {

    public final Map<String, ProjectDeployment> saved = new LinkedHashMap<>();

    public FakeProjectDeploymentRepository() {
        super(null);
    }

    @Override
    public Future<ProjectDeployment> findById(String id, String orgId) {
        return Future.succeededFuture(saved.get(id));
    }

    @Override
    public Future<ProjectDeployment> save(ProjectDeployment value, String orgId) {
        saved.put(value.getId(), value);
        return Future.succeededFuture(value);
    }
}
