package org.kinotic.github.internal.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.security.AuthScopeType;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.kinotic.github.api.services.GitHubAppInstallationService;
import org.kinotic.github.api.services.GitHubRefService;
import org.kinotic.github.internal.api.services.client.GitHubApiClient;
import org.kinotic.github.internal.api.services.client.GitHubInstallationTokenCache;
import org.kinotic.os.api.model.Project;
import org.kinotic.os.api.services.ProjectService;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Default impl: same authorisation as the token service. Obtains a
 * {@code contents:write}-scoped install token then asks {@link GitHubApiClient} to
 * create the ref. The API client treats "Reference already exists" as success so
 * idempotent retries don't need special handling here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultGitHubRefService implements GitHubRefService {

    private final SecurityContext securityContext;
    private final ProjectService projectService;
    private final GitHubAppInstallationService installationService;
    private final GitHubInstallationTokenCache tokenCache;
    private final GitHubApiClient apiClient;

    @Override
    public CompletableFuture<Void> createTag(String organizationId, String projectId, String tagName, String sha) {
        return createRef(organizationId, projectId, "refs/tags/" + tagName, sha);
    }

    @Override
    public CompletableFuture<Void> createBranch(String organizationId, String projectId, String branchName, String sha) {
        return createRef(organizationId, projectId, "refs/heads/" + branchName, sha);
    }

    private CompletableFuture<Void> createRef(String organizationId, String projectId, String refName, String sha) {
        securityContext.requireAuthScope(AuthScopeType.ORGANIZATION, organizationId);
        return projectService.findById(projectId).thenCompose(project -> {
            if (project == null || project.getRepoFullName() == null || project.getRepoId() == null) {
                throw new IllegalStateException(
                        "Project " + projectId + " has no GitHub repo provisioned");
            }
            if (!organizationId.equals(project.getOrganizationId())) {
                throw new IllegalStateException(
                        "Project " + projectId + " does not belong to organization " + organizationId);
            }
            return installationService.findForCurrentOrg().thenCompose(install -> {
                if (install == null) {
                    throw new IllegalStateException(
                            "GitHub install for organization " + organizationId + " no longer exists");
                }
                return mintAndCreate(install, project, refName, sha);
            });
        });
    }

    private CompletableFuture<Void> mintAndCreate(GitHubAppInstallation install,
                                                  Project project,
                                                  String refName,
                                                  String sha) {
        return tokenCache.get(install.getGithubInstallationId(),
                              project.getRepoId(),
                              GitHubInstallationTokenCache.WRITE_CONTENTS)
                         .compose(entry -> apiClient.createRef(entry.token(), project.getRepoFullName(), refName, sha))
                         .toCompletionStage().toCompletableFuture();
    }
}
