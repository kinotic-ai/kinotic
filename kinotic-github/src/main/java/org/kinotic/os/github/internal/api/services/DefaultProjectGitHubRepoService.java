package org.kinotic.os.github.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.os.api.model.iam.AuthScopeType;
import org.kinotic.os.github.api.model.AvailableRepo;
import org.kinotic.os.github.api.model.GitHubAppInstallation;
import org.kinotic.os.github.api.model.ProjectGitHubRepoLink;
import org.kinotic.os.github.api.services.GitHubAppInstallationService;
import org.kinotic.os.github.api.services.ProjectGitHubRepoService;
import org.kinotic.os.github.internal.client.GitHubApiClient;
import org.kinotic.os.github.internal.client.InstallationTokenCache;
import org.kinotic.os.internal.api.services.AbstractCrudService;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Default impl: CRUD over the {@code kinotic_project_github_repo} index plus the
 * three project-scoped operations the SPA calls (list available repos, link, unlink).
 * The org's installation must exist before any link operation can succeed; we look it
 * up via {@link GitHubAppInstallationService} rather than duplicating that lookup.
 */
@Slf4j
@Component
public class DefaultProjectGitHubRepoService
        extends AbstractCrudService<ProjectGitHubRepoLink>
        implements ProjectGitHubRepoService {

    private static final String INDEX = "kinotic_project_github_repo";

    private final GitHubAppInstallationService installationService;
    private final GitHubApiClient apiClient;
    private final InstallationTokenCache tokenCache;

    public DefaultProjectGitHubRepoService(CrudServiceTemplate crudServiceTemplate,
                                           ElasticsearchAsyncClient esAsyncClient,
                                           SecurityContext securityContext,
                                           GitHubAppInstallationService installationService,
                                           GitHubApiClient apiClient,
                                           InstallationTokenCache tokenCache) {
        super(INDEX, ProjectGitHubRepoLink.class, esAsyncClient, crudServiceTemplate, securityContext);
        this.installationService = installationService;
        this.apiClient = apiClient;
        this.tokenCache = tokenCache;
    }

    @Override
    public CompletableFuture<List<AvailableRepo>> listAvailableRepos() {
        requireOrgScope();
        return installationService.findForCurrentOrg().thenCompose(install -> {
            if (install == null) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
            return mintListingToken(install)
                    .thenCompose(token -> apiClient.listInstallationRepos(token).toCompletionStage().toCompletableFuture());
        });
    }

    @Override
    public CompletableFuture<ProjectGitHubRepoLink> linkProject(String projectId, String repoFullName) {
        Participant participant = requireOrgScope();
        return installationService.findForCurrentOrg().thenCompose(install -> {
            if (install == null) {
                throw new IllegalStateException("GitHub is not linked for this organization");
            }
            return mintListingToken(install).thenCompose(token ->
                    apiClient.getRepo(token, repoFullName).toCompletionStage().toCompletableFuture()
                             .thenCompose(repoJson -> persistLink(participant, install, projectId, repoFullName, repoJson)));
        });
    }

    @Override
    public CompletableFuture<Void> unlinkProject(String projectId) {
        requireOrgScope();
        return findByProject(projectId).thenCompose(existing -> {
            if (existing == null) {
                return CompletableFuture.completedFuture(null);
            }
            return deleteById(existing.getId());
        });
    }

    @Override
    public CompletableFuture<ProjectGitHubRepoLink> findByProject(String projectId) {
        // 1:1 link — we use the projectId as the deterministic id for the row.
        return findById(projectId);
    }

    private CompletableFuture<ProjectGitHubRepoLink> persistLink(Participant participant,
                                                                 GitHubAppInstallation install,
                                                                 String projectId,
                                                                 String repoFullName,
                                                                 JsonObject repoJson) {
        ProjectGitHubRepoLink link = new ProjectGitHubRepoLink()
                .setId(projectId)
                .setProjectId(projectId)
                .setOrganizationId(participant.getAuthScopeId())
                .setInstallationId(install.getId())
                .setRepoFullName(repoFullName)
                .setRepoId(String.valueOf(repoJson.getLong("id")))
                .setDefaultBranch(repoJson.getString("default_branch"))
                .setUpdated(new Date());
        return save(link);
    }

    /**
     * Convenience: fetch a low-privilege installation token used only for browsing
     * repos and reading their metadata during the link flow. Permissions are widened
     * later by the dedicated token / ref services for clones and ref creation.
     */
    private CompletableFuture<String> mintListingToken(GitHubAppInstallation install) {
        return tokenCache.get(install.getGithubInstallationId(), null,
                              InstallationTokenCache.READ_CONTENTS)
                         .map(InstallationTokenCache.Entry::token)
                         .toCompletionStage().toCompletableFuture();
    }

    private Participant requireOrgScope() {
        Participant participant = securityContext.currentParticipant();
        if (participant == null
                || !AuthScopeType.ORGANIZATION.name().equals(participant.getAuthScopeType())) {
            throw new AuthorizationException("ORGANIZATION-scoped session required");
        }
        return participant;
    }
}
