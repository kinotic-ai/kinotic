package org.kinotic.github.internal.api.services;

import com.github.slugify.Slugify;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.github.api.config.KinoticGithubProperties;
import org.kinotic.github.api.services.GitHubAppInstallationService;
import org.kinotic.github.internal.api.services.client.CreatedRepository;
import org.kinotic.github.internal.api.services.client.GitHubApiClient;
import org.kinotic.github.internal.api.services.client.TreeEntry;
import org.kinotic.domain.api.model.Project;
import org.kinotic.domain.api.model.RepositoryConnectionStatus;
import org.kinotic.domain.api.services.ProjectRepoProvisioner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Provisions a GitHub repository for a new {@link Project}: generates it from
 * the configured template repo, renders the spawn-format template contents
 * (liquid in paths and {@code .liquid} files) with the project's values, and
 * rewrites the default branch to a single root commit containing the rendered
 * baseline. Honours the caller-supplied {@code repoPrivate} flag and stamps the
 * resulting {@code repoFullName}, {@code repoId}, and {@code defaultBranch} on
 * the project before it is persisted.
 * <p>
 * Slugifies the project name with the same {@link Slugify} configuration used by
 * {@code DefaultProjectService} when deriving the project id, so a name that
 * passes the platform-side id-uniqueness check produces an identically-shaped
 * GitHub repo name.
 */
@Slf4j
@Component
@Profile("!test & !e2e-tests")
@RequiredArgsConstructor
public class GitHubProjectRepoProvisioner implements ProjectRepoProvisioner {

    private static final Slugify SLUGIFY = Slugify.builder().underscoreSeparator(true).build();
    private static final int GITHUB_REPO_NAME_MAX = 100;
    private static final int TARBALL_MAX_ATTEMPTS = 10;
    private static final Duration TARBALL_RETRY_DELAY = Duration.ofSeconds(2);

    private final GitHubAppInstallationService installationService;
    private final GitHubApiClient apiClient;
    private final KinoticGithubProperties properties;
    private final GraalJsSpawnRenderer spawnRenderer;

    @Override
    public CompletableFuture<Project> provision(Project project) {
        String repoName = toRepoName(project.getName());
        if (repoName.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Project name '" + project.getName() + "' produces an empty GitHub repository name"));
        }
        return installationService.findForCurrentOrg().thenCompose(install -> {
            if (install == null) {
                throw new IllegalStateException(
                        "GitHub is not linked for this organization. "
                        + "Link GitHub before creating a project.");
            }
            long installationId = install.getGithubInstallationId();
            // repoId is null — the repo doesn't exist yet, so we mint an
            // installation-wide WRITE_CONTENTS token to create it.
            return apiClient.getToken(installationId, null, GitHubApiClient.WRITE_CONTENTS)
                            .compose(token -> apiClient.createRepoFromTemplate(
                                    token.getToken(),
                                    properties.getGithub().getRepoTemplate(),
                                    install.getAccountLogin(),
                                    repoName,
                                    project.getDescription(),
                                    project.isRepoPrivate()))
                            .map(repo -> stamp(project, repo))
                            .compose(p -> initializeRepo(installationId, p))
                            .toCompletionStage().toCompletableFuture();
        });
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
        return apiClient.downloadTarball(token, project.getRepoFullName(), project.getDefaultBranch())
                        .recover(err -> {
                            if (attemptsLeft <= 1) {
                                return Future.failedFuture(err);
                            }
                            // Generating a repo from a template is asynchronous on GitHub's
                            // side; the tarball 404s until the copied content lands.
                            log.debug("Tarball of {} not ready yet ({} attempts left): {}",
                                      project.getRepoFullName(), attemptsLeft - 1, err.getMessage());
                            return delay(TARBALL_RETRY_DELAY)
                                    .compose(v -> downloadTarballWithRetry(token, project, attemptsLeft - 1));
                        });
    }

    private Future<Project> renderAndPush(String token, Project project, Buffer tarball) {
        // Tarball parsing and the GraalJS render are CPU-bound and blocking, so
        // they run off the calling thread.
        return Future.fromCompletionStage(CompletableFuture.supplyAsync(() -> render(project, tarball)))
                     .compose(baseline -> uploadBinaries(token, project, baseline))
                     .compose(entries -> apiClient.createTree(token, project.getRepoFullName(), entries))
                     .compose(treeSha -> apiClient.createCommit(token, project.getRepoFullName(),
                                                                "Initialize " + project.getName(), treeSha))
                     .compose(commitSha -> apiClient.updateRef(token, project.getRepoFullName(),
                                                               "heads/" + project.getDefaultBranch(),
                                                               commitSha, true))
                     .map(v -> {
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

        Map<String, String> rendered = spawnRenderer.render(textFiles, contextFor(project));

        return new RenderedBaseline(rendered, binaryFiles, entries);
    }

    private Future<List<TreeEntry>> uploadBinaries(String token, Project project, RenderedBaseline baseline) {
        List<TreeEntry> treeEntries = new ArrayList<>();
        baseline.renderedFiles().forEach((path, content) ->
                treeEntries.add(TreeEntry.text(path, modeFor(path, baseline), content)));

        List<Future<Void>> uploads = new ArrayList<>();
        baseline.binaryFiles().forEach((path, file) ->
                uploads.add(apiClient.createBlob(token, project.getRepoFullName(), file.content())
                                     .map(sha -> {
                                         String mode = file.executable() ? TreeEntry.MODE_EXECUTABLE
                                                                         : TreeEntry.MODE_FILE;
                                         treeEntries.add(TreeEntry.blob(path, mode, sha));
                                         return null;
                                     })));
        return Future.all(uploads).map(v -> treeEntries);
    }

    /**
     * The executable bit survives only for files whose path is unchanged by the
     * render (e.g. gradlew); a templated path can't be traced back to its source
     * tarball entry, so it defaults to a regular file.
     */
    private String modeFor(String renderedPath, RenderedBaseline baseline) {
        TarballFile source = baseline.tarballEntries().get(renderedPath);
        return source != null && source.executable() ? TreeEntry.MODE_EXECUTABLE : TreeEntry.MODE_FILE;
    }

    private Map<String, Object> contextFor(Project project) {
        return Map.of("projectName", project.getName(),
                      "organization", project.getOrganizationId(),
                      "application", project.getApplicationId());
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

    private static Future<Void> delay(Duration duration) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        CompletableFuture.delayedExecutor(duration.toMillis(), TimeUnit.MILLISECONDS)
                         .execute(() -> future.complete(null));
        return Future.fromCompletionStage(future);
    }

    private Project stamp(Project project, CreatedRepository repo) {
        project.setRepoFullName(repo.fullName());
        project.setRepoId(repo.id());
        project.setDefaultBranch(repo.defaultBranch());
        project.setRepositoryConnectionStatus(RepositoryConnectionStatus.CONNECTED);
        log.info("Provisioned GitHub repo {} for project {} (org {})",
                 project.getRepoFullName(), project.getId(), project.getOrganizationId());
        return project;
    }

    private static String toRepoName(String projectName) {
        if (projectName == null) return "";
        String s = SLUGIFY.slugify(projectName);
        if (s.length() > GITHUB_REPO_NAME_MAX) s = s.substring(0, GITHUB_REPO_NAME_MAX);
        return s;
    }

    private record RenderedBaseline(Map<String, String> renderedFiles,
                                    Map<String, TarballFile> binaryFiles,
                                    Map<String, TarballFile> tarballEntries) {
    }
}
