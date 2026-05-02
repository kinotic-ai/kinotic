package org.kinotic.github.internal.api.services;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.api.model.Project;
import org.kinotic.os.api.services.ProjectRepoProvisioner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Test/e2e replacement for {@link GitHubProjectRepoProvisioner}: skips the
 * org-install lookup and the GitHub API call entirely, just stamps deterministic
 * fake repo metadata on the project. Lets project-create flow tests pass without
 * seeding a {@code GitHubAppInstallation} row or hitting github.com.
 */
@Slf4j
@Component
@Profile({"test", "e2e-tests"})
public class MockProjectRepoProvisioner implements ProjectRepoProvisioner {

    private static final String FAKE_OWNER = "kinotic-test";
    private static final long FAKE_REPO_ID = 1L;
    private static final String FAKE_DEFAULT_BRANCH = "main";

    @Override
    public CompletableFuture<Project> provision(Project project) {
        String repoName = project.getName() == null ? "unnamed" : project.getName();
        project.setRepoFullName(FAKE_OWNER + "/" + repoName);
        project.setRepoId(FAKE_REPO_ID);
        project.setDefaultBranch(FAKE_DEFAULT_BRANCH);
        log.debug("MockProjectRepoProvisioner stamped {} on project {}",
                  project.getRepoFullName(), project.getId());
        return CompletableFuture.completedFuture(project);
    }
}
