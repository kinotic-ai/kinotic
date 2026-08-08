package org.kinotic.github.internal.api.services;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.internal.api.services.AbstractOrganizationScopedService;
import org.kinotic.github.api.config.KinoticGithubProperties;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.kinotic.github.api.model.GitHubInstallCompletion;
import org.kinotic.github.api.services.GitHubAppInstallationService;
import org.kinotic.github.internal.api.repositories.GitHubAppInstallationRepository;
import org.kinotic.github.internal.api.services.client.GitHubApiClient;
import org.kinotic.github.internal.api.services.client.InstallationDetails;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

/**
 * Default impl of the install round-trip over the {@code kinotic_github_app_installation}
 * index. Inherits org-scope enforcement from {@link AbstractOrganizationScopedService} so a
 * caller cannot read or mutate installations belonging to other orgs; only the operations
 * {@link GitHubAppInstallationService} declares are reachable remotely.
 */
@Slf4j
@Component
public class DefaultGitHubAppInstallationService
        extends AbstractOrganizationScopedService<GitHubAppInstallation>
        implements GitHubAppInstallationService {

    private final GitHubAppInstallationRepository installationRepository;
    private final KinoticGithubProperties properties;
    private final GitHubInstallStateService stateService;
    private final GitHubApiClient apiClient;

    public DefaultGitHubAppInstallationService(GitHubAppInstallationRepository repository,
                                               SecurityContext securityContext,
                                               KinoticGithubProperties properties,
                                               GitHubInstallStateService stateService,
                                               GitHubApiClient apiClient) {
        super(repository, securityContext);
        this.installationRepository = repository;
        this.properties = properties;
        this.stateService = stateService;
        this.apiClient = apiClient;
    }

    @Override
    public CompletableFuture<String> startInstall(String returnTo) {
        String orgId = requireOrganizationId();
        StagedInstall staged = new StagedInstall()
                .setOrganizationId(orgId)
                .setReturnTo(returnTo);
        String state = stateService.stage(staged);
        return CompletableFuture.completedFuture(
                "https://github.com/apps/" + properties.getGithub().getAppSlug()
                        + "/installations/new?state=" + state);
    }

    @Override
    public CompletableFuture<GitHubInstallCompletion> completeInstall(long installationId, String state) {
        String callerOrgId = requireOrganizationId();
        StagedInstall staged = stateService.consume(state);
        if (staged == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Install state is missing, expired, or already used. Please re-link GitHub."));
        }
        if (!callerOrgId.equals(staged.getOrganizationId())) {
            return CompletableFuture.failedFuture(new AuthorizationException(
                    "Install state does not belong to the current organization."));
        }
        return Future.fromCompletionStage(findForCurrentOrg())
                .compose(existing -> existing == null
                                     || Long.valueOf(installationId).equals(existing.getGithubInstallationId())
                        ? apiClient.getInstallation(installationId)
                        : Future.<InstallationDetails>failedFuture(new IllegalStateException(
                                "This organization is already linked to GitHub installation "
                                + existing.getGithubInstallationId()
                                + ". Unlink it before linking another.")))
                .compose(details -> persist(staged.getOrganizationId(), installationId, details))
                .map(installation -> new GitHubInstallCompletion()
                        .setInstallation(installation)
                        .setReturnTo(staged.getReturnTo()))
                .toCompletionStage().toCompletableFuture();
    }

    private Future<GitHubAppInstallation> persist(String orgId, long installationId, InstallationDetails details) {
        Date now = new Date();
        GitHubAppInstallation install = new GitHubAppInstallation()
                .setId(Long.toString(installationId))
                .setOrganizationId(orgId)
                .setGithubInstallationId(installationId)
                .setAccountLogin(details.accountLogin())
                .setAccountType(details.accountType())
                .setCreated(now)
                .setUpdated(now);
        // saveSync (not save) so the row is searchable before completeInstall resolves —
        // the SPA reads it straight back via findForCurrentOrg(), which is a search.
        return Future.fromCompletionStage(saveSync(install));
    }

    @Override
    public CompletableFuture<GitHubAppInstallation> findForCurrentOrg() {
        return installationRepository.findAll(requireOrganizationId(), Pageable.ofSize(1))
                                     .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    @Override
    public CompletableFuture<Void> unlink() {
        String orgId = requireOrganizationId();
        return findForCurrentOrg().thenCompose(
                // deleteByIdSync (not deleteById) so the row is gone from search before unlink
                // resolves — the SPA reads it straight back via findForCurrentOrg(), a search.
                installation -> installation == null
                        ? CompletableFuture.<Void>completedFuture(null)
                        : installationRepository.deleteByIdSync(installation.getId(), orgId));
    }
}
