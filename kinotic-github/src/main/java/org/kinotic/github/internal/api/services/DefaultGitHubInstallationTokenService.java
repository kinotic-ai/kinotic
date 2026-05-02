package org.kinotic.github.internal.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.security.AuthScopeType;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.kinotic.github.api.model.GitHubInstallationToken;
import org.kinotic.github.api.services.GitHubAppInstallationService;
import org.kinotic.github.api.services.GitHubInstallationTokenService;
import org.kinotic.github.internal.api.services.client.GitHubInstallationTokenCache;
import org.kinotic.os.api.model.Project;
import org.kinotic.os.api.services.ProjectService;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Default impl: enforces caller-org match against the {@code @Scope} parameter,
 * looks up the project's GitHub repo metadata, and asks the
 * {@link GitHubInstallationTokenCache} for a {@code contents:read}-scoped token.
 * Audit-logs every issuance so operators can trace which workers obtained which
 * clone tokens.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultGitHubInstallationTokenService implements GitHubInstallationTokenService {

    private final SecurityContext securityContext;
    private final ProjectService projectService;
    private final GitHubAppInstallationService installationService;
    private final GitHubInstallationTokenCache tokenCache;

    @Override
    public CompletableFuture<GitHubInstallationToken> issueRepoToken(String organizationId, String projectId) {
        securityContext.requireAuthScope(AuthScopeType.ORGANIZATION, organizationId);
        Participant participant = securityContext.currentParticipant();
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
                return mintAndAudit(participant, install, project);
            });
        });
    }

    private CompletableFuture<GitHubInstallationToken> mintAndAudit(Participant participant,
                                                                    GitHubAppInstallation install,
                                                                    Project project) {
        return tokenCache.get(install.getGithubInstallationId(),
                              project.getRepoId(),
                              GitHubInstallationTokenCache.READ_CONTENTS)
                         .map(entry -> {
                             log.info("Issued GitHub clone token for project {} (org {}, repo {}) to {}",
                                      project.getId(), project.getOrganizationId(),
                                      project.getRepoFullName(), participant.getId());
                             return new GitHubInstallationToken()
                                     .setToken(entry.token())
                                     .setExpiresAt(entry.expiresAt())
                                     .setCloneUrl("https://github.com/" + project.getRepoFullName() + ".git")
                                     .setDefaultBranch(project.getDefaultBranch());
                         })
                         .toCompletionStage().toCompletableFuture();
    }
}
