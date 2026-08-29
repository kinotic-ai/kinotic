package org.kinotic.management.internal.api.services.github;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.secret.SecretReferenceResolver;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.OidcProviderKind;
import org.kinotic.domain.api.services.security.OrgSignupOidcConfigurationService;
import org.kinotic.domain.internal.api.services.AbstractOrganizationScopedService;
import org.kinotic.management.api.config.GithubProperties;
import org.kinotic.management.api.model.GitHubAppInstallation;
import org.kinotic.management.api.model.GitHubInstallCompletion;
import org.kinotic.management.api.services.github.GitHubAppInstallationService;
import org.kinotic.management.internal.api.repositories.GitHubAppInstallationRepository;
import org.kinotic.management.internal.api.services.github.client.GitHubApiClient;
import org.kinotic.management.internal.api.services.github.client.InstallationDetails;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

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
    private final GithubProperties githubProperties;
    private final GitHubInstallStateService stateService;
    private final GitHubApiClient apiClient;
    private final OrgSignupOidcConfigurationService orgSignupOidcConfigurationService;
    private final SecretReferenceResolver secretReferenceResolver;

    public DefaultGitHubAppInstallationService(GitHubAppInstallationRepository repository,
                                               SecurityContext securityContext,
                                               GithubProperties githubProperties,
                                               GitHubInstallStateService stateService,
                                               GitHubApiClient apiClient,
                                               OrgSignupOidcConfigurationService orgSignupOidcConfigurationService,
                                               SecretReferenceResolver secretReferenceResolver) {
        super(repository, securityContext);
        this.installationRepository = repository;
        this.githubProperties = githubProperties;
        this.stateService = stateService;
        this.apiClient = apiClient;
        this.orgSignupOidcConfigurationService = orgSignupOidcConfigurationService;
        this.secretReferenceResolver = secretReferenceResolver;
    }

    @Override
    public Future<String> startInstall(String returnTo) {
        String orgId = requireOrganizationId();
        StagedInstall staged = new StagedInstall()
                .setOrganizationId(orgId)
                .setReturnTo(returnTo);
        String state = stateService.stage(staged);
        return Future.succeededFuture(
                "https://github.com/apps/" + githubProperties.getAppSlug()
                        + "/installations/new?state=" + state);
    }

    @Override
    public Future<GitHubInstallCompletion> completeInstall(long installationId, String state, String code) {
        String callerOrgId = requireOrganizationId();
        StagedInstall staged = stateService.consume(state);
        if (staged == null) {
            return Future.failedFuture(new IllegalStateException(
                    "Install state is missing, expired, or already used. Please re-link GitHub."));
        }
        if (!callerOrgId.equals(staged.getOrganizationId())) {
            return Future.failedFuture(new AuthorizationException(
                    "Install state does not belong to the current organization."));
        }
        if (code == null || code.isBlank()) {
            return Future.failedFuture(new IllegalStateException(
                    "Install redirect carried no user-authorization code. Please re-link GitHub."));
        }
        return findForCurrentOrg()
                .compose(existing -> existing == null
                                     || Long.valueOf(installationId).equals(existing.getGithubInstallationId())
                        ? verifiedInstallation(installationId, code)
                        : Future.<InstallationDetails>failedFuture(new IllegalStateException(
                                "This organization is already linked to GitHub installation "
                                + existing.getGithubInstallationId()
                                + ". Unlink it before linking another.")))
                .compose(details -> persist(staged.getOrganizationId(), installationId, details))
                .map(installation -> new GitHubInstallCompletion()
                        .setInstallation(installation)
                        .setReturnTo(staged.getReturnTo()));
    }

    private Future<InstallationDetails> verifiedInstallation(long installationId, String code) {
        // The claimed id is browser-supplied and the App's own credentials resolve every
        // installation, other customers' included — only the authorizing user's token
        // proves which installations that user controls.
        return userAccessToken(code)
                .compose(apiClient::listUserInstallations)
                .compose(installations -> requireAccessible(installations, installationId));
    }

    private Future<String> userAccessToken(String code) {
        // Must exchange with the App's own OAuth credential (the github-platform sign-in
        // row): /user/installations only reports installations of the app that minted the token.
        return orgSignupOidcConfigurationService.findEnabledByProvider(OidcProviderKind.GITHUB)
                .compose(config -> {
                    if (config == null) {
                        return Future.failedFuture(new IllegalStateException(
                                "No enabled GitHub OAuth configuration exists to verify the install."));
                    }
                    return secretReferenceResolver.resolve(config.getSecretNameRef())
                            .compose(secret -> {
                                if (secret == null) {
                                    return Future.failedFuture(new IllegalStateException(
                                            "GitHub OAuth client secret '" + config.getSecretNameRef()
                                                    + "' could not be resolved."));
                                }
                                return apiClient.exchangeUserAccessCode(config.getClientId(), secret, code);
                            });
                });
    }

    private Future<InstallationDetails> requireAccessible(List<InstallationDetails> installations,
                                                          long installationId) {
        String appId = githubProperties.getAppId();
        // app_id pin: a credential that is not this App's fails closed instead of
        // vouching from another app's installation list
        InstallationDetails match = installations.stream()
                                                 .filter(details -> details.id() != null
                                                         && details.id() == installationId
                                                         && appId.equals(String.valueOf(details.appId())))
                                                 .findFirst()
                                                 .orElse(null);
        Future<InstallationDetails> ret;
        if (match == null) {
            log.warn("GitHub install verification failed: installation {} is not accessible to the authorizing user",
                     installationId);
            ret = Future.failedFuture(new AuthorizationException(
                    "The GitHub account that authorized this install does not have access to the requested installation."));
        } else {
            ret = Future.succeededFuture(match);
        }
        return ret;
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
        return saveSync(install);
    }

    @Override
    public Future<GitHubAppInstallation> findForCurrentOrg() {
        return installationRepository.findAll(requireOrganizationId(), Pageable.ofSize(1))
                                     .map(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    @Override
    public Future<Void> unlink() {
        String orgId = requireOrganizationId();
        return findForCurrentOrg().compose(
                // deleteByIdSync (not deleteById) so the row is gone from search before unlink
                // resolves — the SPA reads it straight back via findForCurrentOrg(), a search.
                installation -> installation == null
                        ? Future.<Void>succeededFuture()
                        : installationRepository.deleteByIdSync(installation.getId(), orgId));
    }
}
