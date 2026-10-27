package org.kinotic.management.internal.api.services.github;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.ProjectRepoToken;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.kinotic.management.api.model.github.GitHubAppInstallation;
import org.kinotic.management.api.services.github.GitHubProjectRepoService;
import org.kinotic.management.api.services.github.ProjectRepoCredentialService;
import org.kinotic.management.internal.api.repositories.GitHubAppInstallationRepository;
import org.kinotic.management.internal.api.services.github.client.GitHubApiClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kinotic.managementApi.repoHost", havingValue = "GITHUB", matchIfMissing = true)
public class DefaultGitHubProjectRepoService implements GitHubProjectRepoService, ProjectRepoCredentialService {

    private final ProjectRepository projectRepository;
    private final GitHubAppInstallationRepository installationRepository;
    private final GitHubApiClient apiClient;
    private final SecurityContext securityContext;

    @Override
    public Future<ProjectRepoToken> issueRepoToken(String organizationId, String projectId) {
        return issueToken(organizationId, projectId, GitHubApiClient.READ_CONTENTS);
    }

    @Override
    public Future<ProjectRepoToken> issueWriteToken(String projectId) {
        Validate.notBlank(projectId, "projectId is required");
        OrganizationParticipant participant = securityContext.requireParticipant(OrganizationParticipant.class);
        return issueToken(participant.getOrganizationId(), projectId, GitHubApiClient.WRITE_CONTENTS);
    }

    private Future<ProjectRepoToken> issueToken(String organizationId, String projectId, Map<String, String> permissions) {
        return resolve(organizationId, projectId).compose(ctx ->
                apiClient.getToken(ctx.install().getGithubInstallationId(),
                                   ctx.project().getRepoId(),
                                   permissions)
                         .map(base -> new ProjectRepoToken(
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

    // GitHubProjectRepoService callers are trusted in-process code passing the organizationId, and
    // issueWriteToken passes its caller's, so authorization is validation of that organizationId:
    // the org-scoped project lookup finds nothing for a project of another org, and the org must
    // still have a GitHub install.
    private Future<RepoContext> resolve(String organizationId, String projectId) {
        return projectRepository.findById(projectId, organizationId).compose(project -> {
            if (project == null || project.getRepoFullName() == null || project.getRepoId() == null) {
                throw new IllegalStateException("Project " + projectId + " of organization "
                        + organizationId + " has no GitHub repo provisioned");
            }
            return installationRepository.findAll(organizationId, Pageable.ofSize(1))
                    .map(Page::getContent)
                    .map(installs -> {
                        if (installs.isEmpty()) {
                            throw new IllegalStateException(
                                    "GitHub install for organization " + organizationId + " no longer exists");
                        }
                        return new RepoContext(project, installs.getFirst());
                    });
        });
    }

    private record RepoContext(Project project, GitHubAppInstallation install) {}
}
