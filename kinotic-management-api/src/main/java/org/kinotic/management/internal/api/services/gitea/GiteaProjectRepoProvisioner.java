package org.kinotic.management.internal.api.services.gitea;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.kinotic.management.api.config.RepoHostType;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.RepositoryHost;
import org.kinotic.management.api.model.RepositoryConnectionStatus;
import org.kinotic.management.api.services.ProjectRepoProvisioner;
import org.kinotic.management.api.services.github.GitHubWebhookProcessor;
import org.kinotic.management.internal.api.services.github.client.CreatedRepository;
import org.kinotic.management.internal.api.services.github.client.GitHubApiClient;
import org.kinotic.management.internal.api.services.template.ProjectTemplateRenderer;
import org.kinotic.management.internal.api.services.template.RenderedTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Provisions a new {@link Project}'s repository on the Gitea server the system administers: in the Gitea
 * organization named by the project's organization id, holding one commit of the system's project template
 * ({@code kinotic.managementApi.github.repoTemplate}, downloaded from GitHub) rendered for the project
 * ({@link ProjectTemplateRenderer}). The repository is named {@link ProjectTemplateRenderer#repoName}, and given a
 * push webhook to the management server, signed with {@code kinotic.managementApi.github.webhookSecret}, once its
 * contents are committed, so a push deploys the project.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kinotic.managementApi.repoHost", havingValue = "GITEA")
public class GiteaProjectRepoProvisioner implements ProjectRepoProvisioner {

    // The management server as Gitea reaches it on the compose network
    private static final String WEBHOOK_URL = "http://kinotic-server-management:58503" + GitHubWebhookProcessor.WEBHOOK_PATH;

    private final Vertx vertx;
    private final GiteaClient giteaClient;
    private final GitHubApiClient gitHubApiClient;
    private final ProjectTemplateRenderer templateRenderer;
    private final KinoticManagementApiProperties properties;

    @Override
    public Future<Project> provision(Project project) {
        Validate.notBlank(project.getName(), "Project name must not be blank");
        String owner = project.getOrganizationId();
        return giteaClient.ensureOrganization(owner)
                          .compose(v -> giteaClient.createRepository(owner, templateRenderer.repoName(project),
                                                                     project.getDescription(), project.isRepoPrivate()))
                          .map(repo -> stamp(project, repo))
                          // Adopt-not-orphan: the repo exists now, so an initialization failure marks the project for
                          // retry instead of failing the create and leaving an untracked repo behind
                          .compose(p -> initializeRepo(p).recover(err -> {
                              log.error("Initialization failed for {}; repository created, marking project {} {}",
                                        p.getRepoFullName(), p.getId(), RepositoryConnectionStatus.INITIALIZATION_FAILED, err);
                              p.setRepoConnectionStatus(RepositoryConnectionStatus.INITIALIZATION_FAILED);
                              return Future.succeededFuture(p);
                          }));
    }

    @Override
    public Future<Project> reinitialize(Project project) {
        Validate.notBlank(project.getRepoFullName(), "Project repoFullName must be set to reinitialize");
        Validate.notBlank(project.getRepoDefaultBranch(), "Project repoDefaultBranch must be set to reinitialize");
        return initializeRepo(project);
    }

    @Override
    public RepositoryHost repositoryHost() {
        return new RepositoryHost(RepoHostType.GITEA, GiteaClient.WEB_BASE_URL);
    }

    /**
     * Commits the rendered template to a repository that has no commit yet, then gives it its push webhook. The
     * template is committed in one commit, so a repository that has one is initialized already.
     */
    private Future<Project> initializeRepo(Project project) {
        String fullName = project.getRepoFullName();
        return giteaClient.isRepositoryEmpty(fullName)
                          .compose(empty -> empty ? commitTemplate(project) : Future.<Void>succeededFuture())
                          .compose(v -> giteaClient.ensurePushWebhook(fullName, WEBHOOK_URL,
                                                                      properties.getManagementApi().getGithub().getWebhookSecret()))
                          .map(v -> {
                              project.setRepoConnectionStatus(RepositoryConnectionStatus.CONNECTED);
                              log.info("Initialized {} with rendered baseline for project {}", fullName, project.getId());
                              return project;
                          });
    }

    private Future<Void> commitTemplate(Project project) {
        return gitHubApiClient.downloadPublicTarball(properties.getManagementApi().getGithub().getRepoTemplate())
                              // the render is CPU-bound and blocking; ordered=false lets concurrent creations render in parallel
                              .compose(tarball -> vertx.executeBlocking(() -> templateRenderer.render(project, tarball.getBytes()), false))
                              .compose(rendered -> giteaClient.changeFiles(project.getRepoFullName(),
                                                                           project.getRepoDefaultBranch(),
                                                                           "Initialize " + project.getName(),
                                                                           createOperations(rendered)));
    }

    // Gitea's file operations creating every file of the rendered template
    private static JsonArray createOperations(RenderedTemplate rendered) {
        JsonArray ret = new JsonArray();
        rendered.renderedFiles().forEach((path, content) -> ret.add(createOperation(path, content.getBytes(StandardCharsets.UTF_8))));
        rendered.binaryFiles().forEach((path, file) -> ret.add(createOperation(path, file.content())));
        return ret;
    }

    private static JsonObject createOperation(String path, byte[] content) {
        return new JsonObject().put("operation", "create")
                               .put("path", path)
                               .put("content", Base64.getEncoder().encodeToString(content));
    }

    private Project stamp(Project project, CreatedRepository repo) {
        project.setRepoFullName(repo.fullName());
        project.setRepoId(repo.id());
        project.setRepoDefaultBranch(repo.defaultBranch());
        log.info("Provisioned Gitea repo {} for project {} (org {})",
                 project.getRepoFullName(), project.getId(), project.getOrganizationId());
        return project;
    }

}
