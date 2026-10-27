package org.kinotic.management.internal.api.services.github;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.management.api.config.GithubProperties;
import org.kinotic.management.api.model.github.GitHubAppInstallation;
import org.kinotic.management.api.services.github.GitHubAppInstallationService;
import org.kinotic.management.internal.api.services.github.client.CreatedRepository;
import org.kinotic.management.internal.api.services.github.client.GitHubApiClient;
import org.kinotic.management.internal.api.services.github.client.TreeEntry;
import org.kinotic.management.api.config.RepoHostType;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.RepositoryHost;
import org.kinotic.management.api.model.RepositoryConnectionStatus;
import org.kinotic.management.api.services.ProjectRepoProvisioner;
import org.kinotic.management.internal.api.services.template.ProjectTemplateRenderer;
import org.kinotic.management.internal.api.services.template.RenderedTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Provisions a GitHub repository for a new {@link Project}: generates it from
 * the configured template repo, renders the template contents with the project's
 * values ({@link ProjectTemplateRenderer}), and rewrites the default branch to a
 * single root commit containing the rendered baseline. Honours the caller-supplied {@code repoPrivate} flag and stamps the
 * resulting {@code repoFullName}, {@code repoId}, and {@code repoDefaultBranch} on
 * the project before it is persisted.
 * The repository is named {@link ProjectTemplateRenderer#repoName}.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "kinotic.managementApi.github.disableProvisioner", havingValue = "false", matchIfMissing = true)
@ConditionalOnProperty(value = "kinotic.managementApi.repoHost", havingValue = "GITHUB", matchIfMissing = true)
@RequiredArgsConstructor
public class GitHubProjectRepoProvisioner implements ProjectRepoProvisioner {

    private static final int TARBALL_MAX_ATTEMPTS = 10;
    private static final Duration TARBALL_RETRY_DELAY = Duration.ofSeconds(2);
    private static final RepositoryHost GITHUB = new RepositoryHost(RepoHostType.GITHUB, "https://github.com");

    private final Vertx vertx;
    private final GitHubAppInstallationService installationService;
    private final GitHubApiClient apiClient;
    private final GithubProperties githubProperties;
    private final ProjectTemplateRenderer templateRenderer;

    @Override
    public Future<Project> provision(Project project) {
        Validate.notBlank(project.getName(), "Project name must not be blank");
        String repoName = templateRenderer.repoName(project);
        return requireInstallation().compose(install -> {
            long installationId = install.getGithubInstallationId();
            // repoId is null — the repo doesn't exist yet, so we mint an
            // installation-wide CREATE_REPOSITORY token to create it. The generate
            // endpoint 404s when the token lacks administration:write.
            return apiClient.getToken(installationId, null, GitHubApiClient.CREATE_REPOSITORY)
                            .compose(token -> apiClient.createRepoFromTemplate(
                                    token.getToken(),
                                    githubProperties.getRepoTemplate(),
                                    install.getAccountLogin(),
                                    repoName,
                                    project.getDescription(),
                                    project.isRepoPrivate()))
                            .map(repo -> stamp(project, repo))
                            // Adopt-not-orphan: the repo exists now, so a baseline failure
                            // marks the project for retry instead of failing the create and
                            // leaving an untracked repo behind.
                            .compose(p -> initializeRepo(installationId, p).recover(err -> {
                                log.error("Baseline initialization failed for {}; repository created, "
                                          + "marking project {} {}", p.getRepoFullName(), p.getId(),
                                          RepositoryConnectionStatus.INITIALIZATION_FAILED, err);
                                p.setRepoConnectionStatus(RepositoryConnectionStatus.INITIALIZATION_FAILED);
                                return Future.succeededFuture(p);
                            }));
        });
    }

    @Override
    public Future<Project> reinitialize(Project project) {
        Validate.notNull(project.getRepoId(), "Project repoId must be set to reinitialize");
        Validate.notBlank(project.getRepoFullName(), "Project repoFullName must be set to reinitialize");
        Validate.notBlank(project.getRepoDefaultBranch(), "Project repoDefaultBranch must be set to reinitialize");
        return requireInstallation()
                .compose(install -> initializeRepo(install.getGithubInstallationId(), project));
    }

    @Override
    public RepositoryHost repositoryHost() {
        return GITHUB;
    }

    private Future<GitHubAppInstallation> requireInstallation() {
        return installationService.findForCurrentOrg()
                .compose(install -> install == null
                        ? Future.failedFuture(new IllegalStateException(
                                "GitHub is not linked for this organization. "
                                + "Link GitHub before creating a project."))
                        : Future.succeededFuture(install));
    }

    /**
     * Renders the template contents the new repo was generated with and rewrites
     * the default branch to a single root commit of the rendered baseline.
     */
    private Future<Project> initializeRepo(long installationId, Project project) {
        return apiClient.getToken(installationId, project.getRepoId(), GitHubApiClient.WRITE_CONTENTS)
                        .compose(token -> downloadTarballWithRetry(token.getToken(), project, TARBALL_MAX_ATTEMPTS)
                                .compose(tarball -> renderAndPush(token.getToken(), project, tarball)));
    }

    private Future<Buffer> downloadTarballWithRetry(String token, Project project, int attemptsLeft) {
        return apiClient.downloadTarball(token, project.getRepoFullName(), project.getRepoDefaultBranch())
                        .recover(err -> {
                            if (attemptsLeft <= 1) {
                                return Future.failedFuture(err);
                            }
                            // Generating a repo from a template is asynchronous on GitHub's
                            // side; the tarball 404s until the copied content lands.
                            log.debug("Tarball of {} not ready yet ({} attempts left): {}",
                                      project.getRepoFullName(), attemptsLeft - 1, err.getMessage());
                            return vertx.timer(TARBALL_RETRY_DELAY.toMillis(), TimeUnit.MILLISECONDS)
                                        .compose(v -> downloadTarballWithRetry(token, project, attemptsLeft - 1));
                        });
    }

    private Future<Project> renderAndPush(String token, Project project, Buffer tarball) {
        // Tarball parsing and the GraalJS render are CPU-bound and blocking, so they run on
        // the worker pool. ordered=false lets concurrent project creations render in parallel.
        return vertx.executeBlocking(() -> templateRenderer.render(project, tarball.getBytes()), false)
                     .compose(baseline -> uploadBinaries(token, project, baseline))
                     .compose(entries -> apiClient.createTree(token, project.getRepoFullName(), entries))
                     .compose(treeSha -> apiClient.createCommit(token, project.getRepoFullName(),
                                                                "Initialize " + project.getName(), treeSha))
                     .compose(commitSha -> apiClient.updateRef(token, project.getRepoFullName(),
                                                               "heads/" + project.getRepoDefaultBranch(),
                                                               commitSha, true))
                     .map(v -> {
                         project.setRepoConnectionStatus(RepositoryConnectionStatus.CONNECTED);
                         log.info("Initialized {} with rendered baseline for project {}",
                                  project.getRepoFullName(), project.getId());
                         return project;
                     });
    }

    private Future<List<TreeEntry>> uploadBinaries(String token, Project project, RenderedTemplate baseline) {
        List<Future<TreeEntry>> entries = new ArrayList<>();
        baseline.renderedFiles().forEach((path, content) ->
                entries.add(Future.succeededFuture(
                        TreeEntry.text(path, baseline.isExecutable(path) ? TreeEntry.MODE_EXECUTABLE : TreeEntry.MODE_FILE,
                                       content))));

        baseline.binaryFiles().forEach((path, file) ->
                entries.add(apiClient.createBlob(token, project.getRepoFullName(), file.content())
                                     .map(sha -> TreeEntry.blob(path,
                                                                file.executable() ? TreeEntry.MODE_EXECUTABLE
                                                                                  : TreeEntry.MODE_FILE,
                                                                sha))));
        return Future.all(entries).map(CompositeFuture::list);
    }

    private Project stamp(Project project, CreatedRepository repo) {
        project.setRepoFullName(repo.fullName());
        project.setRepoId(repo.id());
        project.setRepoDefaultBranch(repo.defaultBranch());
        log.info("Provisioned GitHub repo {} for project {} (org {})",
                 project.getRepoFullName(), project.getId(), project.getOrganizationId());
        return project;
    }

}
