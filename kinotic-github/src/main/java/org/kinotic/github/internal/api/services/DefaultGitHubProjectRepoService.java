package org.kinotic.github.internal.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.AuthScopeType;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.kinotic.github.api.model.GitHubInstallationToken;
import org.kinotic.github.api.services.GitHubAppInstallationService;
import org.kinotic.github.api.services.GitHubProjectRepoService;
import org.kinotic.github.internal.api.services.client.GitHubApiClient;
import org.kinotic.github.internal.api.services.client.GitHubInstallationTokenCache;
import org.kinotic.os.api.model.Project;
import org.kinotic.os.api.services.ProjectService;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultGitHubProjectRepoService implements GitHubProjectRepoService {

    private final SecurityContext securityContext;
    private final ProjectService projectService;
    private final GitHubAppInstallationService installationService;
    private final GitHubInstallationTokenCache tokenCache;
    private final GitHubApiClient apiClient;

    @Override
    public CompletableFuture<GitHubInstallationToken> issueRepoToken(String organizationId, String projectId) {
        return resolve(organizationId, projectId).thenCompose(ctx ->
                tokenCache.get(ctx.install().getGithubInstallationId(),
                               ctx.project().getRepoId(),
                               GitHubInstallationTokenCache.READ_CONTENTS)
                          // Defensive copy: cached token has worker-clone fields null and is shared
                          // across all callers for this (install, repo, perms) key. Build a fresh
                          // instance so stamping cloneUrl/defaultBranch doesn't leak into the cache.
                          .map(cached -> new GitHubInstallationToken()
                                  .setToken(cached.getToken())
                                  .setExpiresAt(cached.getExpiresAt())
                                  .setCloneUrl("https://github.com/" + ctx.project().getRepoFullName() + ".git")
                                  .setDefaultBranch(ctx.project().getDefaultBranch()))
                          .toCompletionStage().toCompletableFuture());
    }

    @Override
    public CompletableFuture<Void> createTag(String organizationId, String projectId, String tagName, String sha) {
        return createRef(organizationId, projectId, "refs/tags/" + tagName, sha);
    }

    @Override
    public CompletableFuture<Void> createBranch(String organizationId, String projectId, String branchName, String sha) {
        return createRef(organizationId, projectId, "refs/heads/" + branchName, sha);
    }

    private CompletableFuture<Void> createRef(String organizationId, String projectId, String refName, String sha) {
        return resolve(organizationId, projectId).thenCompose(ctx ->
                tokenCache.get(ctx.install().getGithubInstallationId(),
                               ctx.project().getRepoId(),
                               GitHubInstallationTokenCache.WRITE_CONTENTS)
                          .compose(token -> apiClient.createRef(token.getToken(),
                                                                ctx.project().getRepoFullName(),
                                                                refName, sha))
                          .toCompletionStage().toCompletableFuture());
    }

    private CompletableFuture<RepoContext> resolve(String organizationId, String projectId) {

        securityContext.requireAuthScope(AuthScopeType.ORGANIZATION, organizationId);

        return projectService.findById(projectId).thenCompose(project -> {
            if (project == null || project.getRepoFullName() == null || project.getRepoId() == null) {
                throw new IllegalStateException(
                        "Project " + projectId + " has no GitHub repo provisioned");
            }
            if (!organizationId.equals(project.getOrganizationId())) {
                throw new AuthorizationException(
                        "Project " + projectId + " does not belong to organization " + organizationId);
            }
            return installationService.findForCurrentOrg().thenApply(install -> {
                if (install == null) {
                    throw new IllegalStateException(
                            "GitHub install for organization " + organizationId + " no longer exists");
                }
                return new RepoContext(project, install);
            });
        });
    }

    private record RepoContext(Project project, GitHubAppInstallation install) {}
}
