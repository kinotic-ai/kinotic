package org.kinotic.github.internal.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.AuthScopeType;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.kinotic.github.api.model.GitHubInstallationToken;
import org.kinotic.github.api.model.ProjectGitHubRepoLink;
import org.kinotic.github.api.services.GitHubAppInstallationService;
import org.kinotic.github.api.services.GitHubInstallationTokenService;
import org.kinotic.github.api.services.ProjectGitHubRepoService;
import org.kinotic.github.internal.api.services.client.GitHubInstallationTokenCache;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Default impl: enforces caller-org match against the {@code @Scope} parameter, looks
 * up the project's {@link ProjectGitHubRepoLink}, and asks the
 * {@link GitHubInstallationTokenCache} for a {@code contents:read}-scoped token.
 * Audit-logs every issuance so operators can trace which workers obtained which
 * clone tokens.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultGitHubInstallationTokenService implements GitHubInstallationTokenService {

    private final SecurityContext securityContext;
    private final ProjectGitHubRepoService repoService;
    private final GitHubAppInstallationService installationService;
    private final GitHubInstallationTokenCache tokenCache;

    @Override
    public CompletableFuture<GitHubInstallationToken> issueRepoToken(String organizationId, String projectId) {
        securityContext.requireAuthScope(AuthScopeType.ORGANIZATION, organizationId);
        Participant participant = securityContext.currentParticipant();
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
                return mintAndAudit(participant, install, link);
            });
        });
    }

    private CompletableFuture<GitHubInstallationToken> mintAndAudit(Participant participant,
                                                                    GitHubAppInstallation install,
                                                                    ProjectGitHubRepoLink link) {
        return tokenCache.get(install.getGithubInstallationId(),
                              link.getRepoId(),
                              GitHubInstallationTokenCache.READ_CONTENTS)
                         .map(entry -> {
                             log.info("Issued GitHub clone token for project {} (org {}, repo {}) to {}",
                                      link.getProjectId(), link.getOrganizationId(),
                                      link.getRepoFullName(), participant.getId());
                             return new GitHubInstallationToken()
                                     .setToken(entry.token())
                                     .setExpiresAt(entry.expiresAt())
                                     .setCloneUrl("https://github.com/" + link.getRepoFullName() + ".git")
                                     .setDefaultBranch(link.getDefaultBranch());
                         })
                         .toCompletionStage().toCompletableFuture();
    }
}
