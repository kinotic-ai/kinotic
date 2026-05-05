package org.kinotic.github.internal.api.services;

import com.github.slugify.Slugify;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.github.api.config.KinoticGithubProperties;
import org.kinotic.github.api.services.GitHubAppInstallationService;
import org.kinotic.github.internal.api.services.client.CreatedRepository;
import org.kinotic.github.internal.api.services.client.GitHubApiClient;
import org.kinotic.os.api.model.Project;
import org.kinotic.os.api.model.RepositoryConnectionStatus;
import org.kinotic.os.api.services.ProjectRepoProvisioner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Provisions a GitHub repository for a new {@link Project} by generating it from
 * the configured template repo. Honours the caller-supplied {@code repoPrivate}
 * flag and stamps the resulting {@code repoFullName}, {@code repoId}, and
 * {@code defaultBranch} on the project before it is persisted.
 * <p>
 * Slugifies the project name with the same {@link Slugify} configuration used by
 * {@code DefaultProjectService} when deriving the project id, so a name that
 * passes the platform-side id-uniqueness check produces an identically-shaped
 * GitHub repo name.
 */
@Slf4j
@Component
@Profile("!test & !e2e-tests")
@RequiredArgsConstructor
public class GitHubProjectRepoProvisioner implements ProjectRepoProvisioner {

    private static final Slugify SLUGIFY = Slugify.builder().underscoreSeparator(true).build();
    private static final int GITHUB_REPO_NAME_MAX = 100;

    private final GitHubAppInstallationService installationService;
    private final GitHubApiClient apiClient;
    private final KinoticGithubProperties properties;

    @Override
    public CompletableFuture<Project> provision(Project project) {
        String repoName = toRepoName(project.getName());
        if (repoName.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Project name '" + project.getName() + "' produces an empty GitHub repository name"));
        }
        return installationService.findForCurrentOrg().thenCompose(install -> {
            if (install == null) {
                throw new IllegalStateException(
                        "GitHub is not linked for this organization. "
                        + "Link GitHub before creating a project.");
            }
            // repoId is null — the repo doesn't exist yet, so we mint an
            // installation-wide WRITE_CONTENTS token to create it.
            return apiClient.getToken(install.getGithubInstallationId(), null,
                                      GitHubApiClient.WRITE_CONTENTS)
                            .compose(token -> apiClient.createRepoFromTemplate(
                                    token.getToken(),
                                    properties.getGithub().getRepoTemplate(),
                                    install.getAccountLogin(),
                                    repoName,
                                    project.getDescription(),
                                    project.isRepoPrivate()))
                            .map(repo -> stamp(project, repo))
                            .toCompletionStage().toCompletableFuture();
        });
    }

    private Project stamp(Project project, CreatedRepository repo) {
        project.setRepoFullName(repo.fullName());
        project.setRepoId(repo.id());
        project.setDefaultBranch(repo.defaultBranch());
        project.setRepositoryConnectionStatus(RepositoryConnectionStatus.CONNECTED);
        log.info("Provisioned GitHub repo {} for project {} (org {})",
                 project.getRepoFullName(), project.getId(), project.getOrganizationId());
        return project;
    }

    private static String toRepoName(String projectName) {
        if (projectName == null) return "";
        String s = SLUGIFY.slugify(projectName);
        if (s.length() > GITHUB_REPO_NAME_MAX) s = s.substring(0, GITHUB_REPO_NAME_MAX);
        return s;
    }
}
