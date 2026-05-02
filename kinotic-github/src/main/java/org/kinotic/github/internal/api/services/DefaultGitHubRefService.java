package org.kinotic.github.internal.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.os.api.model.iam.AuthScopeType;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.kinotic.github.api.model.ProjectGitHubRepoLink;
import org.kinotic.github.api.services.GitHubAppInstallationService;
import org.kinotic.github.api.services.GitHubRefService;
import org.kinotic.github.api.services.ProjectGitHubRepoService;
import org.kinotic.github.internal.client.GitHubApiClient;
import org.kinotic.github.internal.client.GitHubInstallationTokenCache;
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
    private final ProjectGitHubRepoService repoService;
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
        requireMatchingOrg(organizationId);
        return repoService.findByProject(projectId).thenCompose(link -> {
            if (link == null) {
                throw new IllegalStateException(
                        "Project " + projectId + " is not linked to a GitHub repo");
            }
            if (!organizationId.equals(link.getOrganizationId())) {
                throw new AuthorizationException(
                        "Project " + projectId + " does not belong to organization " + organizationId);
            }
            return installationService.findById(link.getInstallationId()).thenCompose(install -> {
                if (install == null) {
                    throw new IllegalStateException(
                            "Installation " + link.getInstallationId() + " no longer exists");
                }
                return mintAndCreate(install, link, refName, sha);
            });
        });
    }

    private CompletableFuture<Void> mintAndCreate(GitHubAppInstallation install,
                                                  ProjectGitHubRepoLink link,
                                                  String refName,
                                                  String sha) {
        return tokenCache.get(install.getGithubInstallationId(),
                              link.getRepoId(),
                              GitHubInstallationTokenCache.WRITE_CONTENTS)
                         .compose(entry -> apiClient.createRef(entry.token(), link.getRepoFullName(), refName, sha))
                         .toCompletionStage().toCompletableFuture();
    }

    private void requireMatchingOrg(String organizationId) {
        Participant participant = securityContext.currentParticipant();
        if (participant == null) {
            throw new AuthorizationException("Authenticated session required");
        }
        if (!AuthScopeType.ORGANIZATION.name().equals(participant.getAuthScopeType())
                || !organizationId.equals(participant.getAuthScopeId())) {
            throw new AuthorizationException(
                    "Caller's organization does not match requested " + organizationId);
        }
    }
}
