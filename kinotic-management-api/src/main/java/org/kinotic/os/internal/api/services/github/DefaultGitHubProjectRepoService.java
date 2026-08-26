package org.kinotic.os.internal.api.services.github;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Project;
import org.kinotic.domain.api.model.ProjectRepoToken;
import org.kinotic.domain.internal.api.repositories.ProjectRepository;
import org.kinotic.domain.api.model.GitHubAppInstallation;
import org.kinotic.os.api.services.GitHubProjectRepoService;
import org.kinotic.domain.internal.api.repositories.GitHubAppInstallationRepository;
import org.kinotic.os.internal.api.services.github.client.GitHubApiClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultGitHubProjectRepoService implements GitHubProjectRepoService {

    private final ProjectRepository projectRepository;
    private final GitHubAppInstallationRepository installationRepository;
    private final GitHubApiClient apiClient;

    @Override
    public Future<ProjectRepoToken> issueRepoToken(String organizationId, String projectId) {
        return resolve(organizationId, projectId).compose(ctx ->
                apiClient.getToken(ctx.install().getGithubInstallationId(),
                                   ctx.project().getRepoId(),
                                   GitHubApiClient.READ_CONTENTS)
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

    // Callers are trusted in-process code (this service is not published), so authorization
    // is validation of the explicit organizationId: the org-scoped project lookup finds
    // nothing for a project of another org, and the org must still have a GitHub install.
    // Participant enforcement returns with @Publish.
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
