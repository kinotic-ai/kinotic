package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.RepositoryConnectionStatus;
import org.kinotic.management.api.services.ProjectRepoProvisioner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback {@link ProjectRepoProvisioner} used when repository provisioning is disabled
 * ({@code kinotic.managementApi.github.disableProvisioner=true}). Skips the GitHub API call entirely and stamps
 * deterministic fake repo metadata on the project, so project-create flows work in
 * development and tests without a configured GitHub App.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "kinotic.managementApi.github.disableProvisioner", havingValue = "true")
public class MockProjectRepoProvisioner implements ProjectRepoProvisioner {

    private static final String FAKE_OWNER = "kinotic-mock";
    private static final long FAKE_REPO_ID = 1L;
    private static final String FAKE_DEFAULT_BRANCH = "main";

    @Override
    public Future<Project> provision(Project project) {
        String repoName = project.getName() == null ? "unnamed" : project.getName();
        project.setRepoFullName(FAKE_OWNER + "/" + repoName);
        project.setRepoId(FAKE_REPO_ID);
        project.setRepoDefaultBranch(FAKE_DEFAULT_BRANCH);
        project.setRepoConnectionStatus(RepositoryConnectionStatus.CONNECTED);
        log.debug("MockProjectRepoProvisioner stamped {} on project {}",
                  project.getRepoFullName(), project.getId());
        return Future.succeededFuture(project);
    }

    @Override
    public Future<Project> reinitialize(Project project) {
        project.setRepoConnectionStatus(RepositoryConnectionStatus.CONNECTED);
        return Future.succeededFuture(project);
    }
}
