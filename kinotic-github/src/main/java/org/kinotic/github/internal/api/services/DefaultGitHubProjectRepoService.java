package org.kinotic.github.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.OrganizationParticipant;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.kinotic.github.api.model.GitHubRepoToken;
import org.kinotic.github.api.services.GitHubAppInstallationService;
import org.kinotic.github.api.services.GitHubProjectRepoService;
import org.kinotic.github.internal.api.services.client.GitHubApiClient;
import org.kinotic.domain.api.model.Project;
import org.kinotic.os.api.services.ProjectService;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultGitHubProjectRepoService implements GitHubProjectRepoService {

    private final SecurityContext securityContext;
    private final ProjectService projectService;
    private final GitHubAppInstallationService installationService;
    private final GitHubApiClient apiClient;

    @Override
    public Future<GitHubRepoToken> issueRepoToken(String organizationId, String projectId) {
        return resolve(organizationId, projectId).compose(ctx ->
                apiClient.getToken(ctx.install().getGithubInstallationId(),
                                   ctx.project().getRepoId(),
                                   GitHubApiClient.READ_CONTENTS)
                         .map(base -> new GitHubRepoToken(
                                 base.getToken(),
                                 base.getExpiresAt(),
                                 "https://github.com/" + ctx.project().getRepoFullName() + ".git",
                                 ctx.project().getRepoDefaultBranch())));
    }

    @Override
    public Future<Void> createTag(String organizationId, String projectId, String tagName, String sha) {
        return createRef(organizationId, projectId, "refs/tags/" + tagName, sha);
    }

    @Override
    public Future<Void> createBranch(String organizationId, String projectId, String branchName, String sha) {
        return createRef(organizationId, projectId, "refs/heads/" + branchName, sha);
    }

    private Future<Void> createRef(String organizationId, String projectId, String refName, String sha) {
        return resolve(organizationId, projectId).compose(ctx ->
                apiClient.getToken(ctx.install().getGithubInstallationId(),
                                   ctx.project().getRepoId(),
                                   GitHubApiClient.WRITE_CONTENTS)
                         .compose(token -> apiClient.createRef(token.getToken(),
                                                               ctx.project().getRepoFullName(),
                                                               refName, sha)));
    }

    private Future<RepoContext> resolve(String organizationId, String projectId) {

        OrganizationParticipant caller = securityContext.requireParticipant(OrganizationParticipant.class);
        if (!organizationId.equals(caller.getOrganizationId())) {
            throw new AuthorizationException(
                    "Caller's organizationId '" + caller.getOrganizationId()
                            + "' does not match requested '" + organizationId + "'");
        }

        return projectService.findById(projectId).compose(project -> {
            if (project == null || project.getRepoFullName() == null || project.getRepoId() == null) {
                throw new IllegalStateException(
                        "Project " + projectId + " has no GitHub repo provisioned");
            }
            if (!organizationId.equals(project.getOrganizationId())) {
                throw new AuthorizationException(
                        "Project " + projectId + " does not belong to organization " + organizationId);
            }
            return installationService.findForCurrentOrg().map(install -> {
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
