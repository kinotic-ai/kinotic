package org.kinotic.github.internal.api.services;

import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.github.api.config.KinoticGithubProperties;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.kinotic.github.api.services.GitHubAppInstallationService;
import org.kinotic.github.internal.api.services.client.GitHubApiClient;
import org.kinotic.github.internal.api.services.client.GitHubInstallationTokenCache;
import org.kinotic.os.api.model.Project;
import org.kinotic.os.api.services.ProjectRepoProvisioner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Provisions a private GitHub repository for a new {@link Project} by generating it
 * from the configured template repo. Stamps the resulting {@code repoFullName},
 * {@code repoId}, and {@code defaultBranch} on the project before it is persisted.
 */
@Slf4j
@Component
@Profile("!test & !e2e-tests")
@RequiredArgsConstructor
public class GitHubProjectRepoProvisioner implements ProjectRepoProvisioner {

    /** GitHub repository names: alphanumeric, hyphen, underscore, period. */
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._-]+");
    private static final Pattern COLLAPSE = Pattern.compile("-{2,}");

    private final GitHubAppInstallationService installationService;
    private final GitHubInstallationTokenCache tokenCache;
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
            return mintRepoCreateToken(install)
                    .thenCompose(token -> apiClient.createRepoFromTemplate(
                                token,
                                properties.getGithub().getRepoTemplate(),
                                install.getAccountLogin(),
                                repoName,
                                project.getDescription())
                            .toCompletionStage().toCompletableFuture())
                    .thenApply(repoJson -> stamp(project, repoJson));
        });
    }

    private CompletableFuture<String> mintRepoCreateToken(GitHubAppInstallation install) {
        return tokenCache.get(install.getGithubInstallationId(), null,
                              GitHubInstallationTokenCache.WRITE_CONTENTS)
                         .map(GitHubInstallationTokenCache.Entry::token)
                         .toCompletionStage().toCompletableFuture();
    }

    private Project stamp(Project project, JsonObject repoJson) {
        project.setRepoFullName(repoJson.getString("full_name"));
        project.setRepoId(repoJson.getLong("id"));
        project.setDefaultBranch(repoJson.getString("default_branch"));
        log.info("Provisioned GitHub repo {} for project {} (org {})",
                 project.getRepoFullName(), project.getId(), project.getOrganizationId());
        return project;
    }

    /** Normalises a project name to a GitHub-safe repo name. */
    private static String toRepoName(String projectName) {
        if (projectName == null) return "";
        String s = UNSAFE.matcher(projectName.trim()).replaceAll("-");
        s = COLLAPSE.matcher(s).replaceAll("-");
        s = s.replaceAll("^[-._]+", "").replaceAll("[-._]+$", "");
        if (s.length() > 100) s = s.substring(0, 100);
        return s;
    }
}
