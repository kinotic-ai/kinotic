package org.kinotic.os.internal.api.services.github;

import com.github.slugify.Slugify;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.os.api.config.github.KinoticGithubProperties;
import org.kinotic.domain.api.model.GitHubAppInstallation;
import org.kinotic.os.api.services.GitHubAppInstallationService;
import org.kinotic.os.internal.api.services.github.client.CreatedRepository;
import org.kinotic.os.internal.api.services.github.client.GitHubApiClient;
import org.kinotic.os.internal.api.services.github.client.TreeEntry;
import org.kinotic.domain.api.model.Project;
import org.kinotic.domain.api.model.RepositoryConnectionStatus;
import org.kinotic.domain.api.services.ProjectRepoProvisioner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Provisions a GitHub repository for a new {@link Project}: generates it from
 * the configured template repo, renders the spawn-format template contents
 * (liquid in paths and {@code .liquid} files) with the project's values, and
 * rewrites the default branch to a single root commit containing the rendered
 * baseline. Honours the caller-supplied {@code repoPrivate} flag and stamps the
 * resulting {@code repoFullName}, {@code repoId}, and {@code repoDefaultBranch} on
 * the project before it is persisted.
 * <p>
 * Slugifies the project name with the same {@link Slugify} configuration used by
 * {@code DefaultProjectService} when deriving the project id, so a name that
 * passes the platform-side id-uniqueness check produces an identically-shaped
 * GitHub repo name. That slug is also exposed to the templates as
 * {@code projectSlug} for values that must be identifiers rather than the raw name;
 * the repo name is the same slug truncated to GitHub's repo-name length limit.
 * <p>
 * Alongside the project's own values, the render context carries a version range for
 * every published {@code @kinotic-ai} npm package, keyed by the spawn global a template
 * pins it through ({@code kinoticCoreVersion}, {@code kinoticCliVersion}, ...), so a
 * provisioned project depends on the package versions this server ships with. The
 * OpenTelemetry ranges those packages are built against travel the same way
 * ({@code otelSdkNodeVersion}, ...), so a project exports its telemetry through an SDK
 * that matches the API the platform emits spans with.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubProjectRepoProvisioner implements ProjectRepoProvisioner {

    private static final Slugify SLUGIFY = Slugify.builder().build();
    private static final int GITHUB_REPO_NAME_MAX = 100;
    private static final int TARBALL_MAX_ATTEMPTS = 10;
    private static final Duration TARBALL_RETRY_DELAY = Duration.ofSeconds(2);
    private static final String NPM_PACKAGE_VERSIONS_RESOURCE = "/spawn/npm-package-versions.properties";
    private static final Map<String, Object> NPM_PACKAGE_VERSIONS = loadNpmPackageVersions();

    private final Vertx vertx;
    private final GitHubAppInstallationService installationService;
    private final GitHubApiClient apiClient;
    private final KinoticGithubProperties properties;
    private final GraalJsSpawnRenderer spawnRenderer;

    @Override
    public Future<Project> provision(Project project) {
        Validate.notBlank(project.getName(), "Project name must not be blank");
        String repoName = toRepoName(project.getName());
        return requireInstallation().compose(install -> {
            long installationId = install.getGithubInstallationId();
            // repoId is null — the repo doesn't exist yet, so we mint an
            // installation-wide CREATE_REPOSITORY token to create it. The generate
            // endpoint 404s when the token lacks administration:write.
            return apiClient.getToken(installationId, null, GitHubApiClient.CREATE_REPOSITORY)
                            .compose(token -> apiClient.createRepoFromTemplate(
                                    token.getToken(),
                                    properties.getGithub().getRepoTemplate(),
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
        return vertx.executeBlocking(() -> render(project, tarball), false)
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

    private RenderedBaseline render(Project project, Buffer tarball) {
        Map<String, TarballFile> entries = TemplateTarball.parse(tarball.getBytes());

        Map<String, String> textFiles = new LinkedHashMap<>();
        Map<String, TarballFile> binaryFiles = new LinkedHashMap<>();
        for (Map.Entry<String, TarballFile> entry : entries.entrySet()) {
            String text = decodeUtf8(entry.getValue().content());
            if (text != null) {
                textFiles.put(entry.getKey(), text);
            } else {
                // Binary files bypass the renderer: tree entries can't carry binary
                // content inline, and liquid path templating doesn't apply to them.
                binaryFiles.put(entry.getKey(), entry.getValue());
            }
        }

        SpawnRenderResult rendered = spawnRenderer.render(textFiles, contextFor(project));

        return new RenderedBaseline(rendered.files(), rendered.sources(), binaryFiles, entries);
    }

    private Future<List<TreeEntry>> uploadBinaries(String token, Project project, RenderedBaseline baseline) {
        List<Future<TreeEntry>> entries = new ArrayList<>();
        // Rendered destinations are re-checked here: a property value can inject ../
        // into a path the source entry didn't contain, so it isn't covered by the
        // parse-time check on tarball entries.
        baseline.renderedFiles().forEach((path, content) ->
                entries.add(Future.succeededFuture(
                        TreeEntry.text(RepoTreePath.requireContained(path), modeFor(path, baseline), content))));

        baseline.binaryFiles().forEach((path, file) ->
                entries.add(apiClient.createBlob(token, project.getRepoFullName(), file.content())
                                     .map(sha -> TreeEntry.blob(path,
                                                                file.executable() ? TreeEntry.MODE_EXECUTABLE
                                                                                  : TreeEntry.MODE_FILE,
                                                                sha))));
        return Future.all(entries).map(CompositeFuture::list);
    }

    /**
     * Carries the executable bit from the source tarball entry to the rendered
     * file, tracing through the render's source map so it survives a templated
     * path (e.g. {@code bin/{{projectName}}.sh.liquid}), not just an unchanged one.
     */
    private String modeFor(String renderedPath, RenderedBaseline baseline) {
        String sourcePath = baseline.sources().get(renderedPath);
        TarballFile source = sourcePath != null ? baseline.tarballEntries().get(sourcePath) : null;
        return source != null && source.executable() ? TreeEntry.MODE_EXECUTABLE : TreeEntry.MODE_FILE;
    }

    private Map<String, Object> contextFor(Project project) {
        // The package versions seed the context, so a template that also declares them as
        // spawn.json globals gets this server's values instead of its own defaults.
        Map<String, Object> ret = new LinkedHashMap<>(NPM_PACKAGE_VERSIONS);
        // projectName is the human-facing name; projectSlug is its slug, for template
        // values that must be npm identifiers (e.g. the package.json name), which the
        // raw name is not. It is not truncated to the repo-name cap: an npm identifier
        // shouldn't inherit GitHub's repo-name length limit.
        ret.put("projectName", project.getName());
        ret.put("projectSlug", SLUGIFY.slugify(project.getName()));
        ret.put("organizationId", project.getOrganizationId());
        ret.put("applicationId", project.getApplicationId());
        return ret;
    }

    private static Map<String, Object> loadNpmPackageVersions() {
        Properties properties = new Properties();
        try (InputStream in = GitHubProjectRepoProvisioner.class.getResourceAsStream(NPM_PACKAGE_VERSIONS_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Spawn context resource " + NPM_PACKAGE_VERSIONS_RESOURCE + " is missing from the classpath. "
                        + "It is generated from the kinotic-js package.json files by the "
                        + "kinotic-github npmPackageVersions task.");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load " + NPM_PACKAGE_VERSIONS_RESOURCE, e);
        }
        Map<String, Object> ret = new LinkedHashMap<>();
        properties.forEach((name, range) -> ret.put((String) name, range));
        return Map.copyOf(ret);
    }

    /** @return the bytes decoded as strict UTF-8, or null when they are not valid UTF-8 (binary). */
    private static String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private Project stamp(Project project, CreatedRepository repo) {
        project.setRepoFullName(repo.fullName());
        project.setRepoId(repo.id());
        project.setRepoDefaultBranch(repo.defaultBranch());
        log.info("Provisioned GitHub repo {} for project {} (org {})",
                 project.getRepoFullName(), project.getId(), project.getOrganizationId());
        return project;
    }

    private static String toRepoName(String projectName) {
        String s = SLUGIFY.slugify(projectName);
        if (s.length() > GITHUB_REPO_NAME_MAX) s = s.substring(0, GITHUB_REPO_NAME_MAX);
        return s;
    }

    private record RenderedBaseline(Map<String, String> renderedFiles,
                                    Map<String, String> sources,
                                    Map<String, TarballFile> binaryFiles,
                                    Map<String, TarballFile> tarballEntries) {
    }
}
