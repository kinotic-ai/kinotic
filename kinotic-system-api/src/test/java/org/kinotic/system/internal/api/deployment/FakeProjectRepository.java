package org.kinotic.system.internal.api.deployment;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.Project;
import org.kinotic.domain.internal.api.repositories.ProjectRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Serves the given projects from findById without touching Elasticsearch.
 */
public class FakeProjectRepository extends ProjectRepository {

    private final Map<String, Project> projects = new HashMap<>();

    public FakeProjectRepository(Project... entities) {
        super(null);
        for (Project project : entities) {
            projects.put(project.getId(), project);
        }
    }

    @Override
    public Future<Project> findById(String id, String orgId) {
        Project project = projects.get(id);
        boolean visible = project != null && orgId.equals(project.getOrganizationId());
        return Future.succeededFuture(visible ? project : null);
    }
}
