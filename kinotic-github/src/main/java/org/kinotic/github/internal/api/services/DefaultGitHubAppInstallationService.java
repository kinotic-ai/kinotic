package org.kinotic.github.internal.api.services;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.secret.SecretReferenceResolver;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.OidcProviderKind;
import org.kinotic.domain.api.services.security.OrgSignupOidcConfigurationService;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Default impl: CRUD over the {@code kinotic_github_app_installation} index plus the
 * three install-flow methods ({@link #startInstall(String)},
 * {@link #completeInstall(long, String, String)}, {@link #findForCurrentOrg()}). Inherits
 * org-scope filtering from {@link AbstractOrganizationScopedService} so callers cannot read
 * or mutate installations belonging to other orgs.
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
    private final OrgSignupOidcConfigurationService orgSignupOidcConfigurationService;
    private final SecretReferenceResolver secretReferenceResolver;

    public DefaultGitHubAppInstallationService(GitHubAppInstallationRepository repository,
                                               SecurityContext securityContext,
                                               KinoticGithubProperties properties,
                                               GitHubInstallStateService stateService,
                                               GitHubApiClient apiClient,
                                               OrgSignupOidcConfigurationService orgSignupOidcConfigurationService,
                                               SecretReferenceResolver secretReferenceResolver) {
        super(repository, securityContext);
        this.installationRepository = repository;
        this.properties = properties;
        this.stateService = stateService;
        this.apiClient = apiClient;
        this.orgSignupOidcConfigurationService = orgSignupOidcConfigurationService;
        this.secretReferenceResolver = secretReferenceResolver;
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
    public CompletableFuture<GitHubInstallCompletion> completeInstall(long installationId, String state, String code) {
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
        if (code == null || code.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Install redirect carried no user-authorization code. Please re-link GitHub."));
        }
        return verifiedInstallation(installationId, code)
                .compose(details -> persist(staged.getOrganizationId(), installationId, details))
                .map(installation -> new GitHubInstallCompletion()
                        .setInstallation(installation)
                        .setReturnTo(staged.getReturnTo()))
                .toCompletionStage().toCompletableFuture();
    }

    /**
     * Resolves the claimed installation to the entry GitHub reports as accessible to
     * the user who authorized during this install round-trip, failing with
     * {@link AuthorizationException} when the user cannot access it.
     */
    private Future<InstallationDetails> verifiedInstallation(long installationId, String code) {
        // The installation id arrives from the browser, so it is attacker-controlled, and
        // the App's own credentials cannot vet it — the App can read every one of its
        // installations, other customers' included. Only the authorizing user's own token
        // scopes the lookup to installations that user actually controls.
        return userAccessToken(code)
                .compose(apiClient::listUserInstallations)
                .compose(installations -> requireAccessible(installations, installationId));
    }

    /**
     * Exchanges the install-time user-authorization code for the authorizing user's
     * access token.
     */
    private Future<String> userAccessToken(String code) {
        // /user/installations only reports installations of the App that minted the user
        // token, so the exchange must use the platform GitHub App's own OAuth credential —
        // the same github-platform row that signs users in. A separate OAuth client could
        // never attest to this App's installations.
        return Future.fromCompletionStage(orgSignupOidcConfigurationService.findEnabledByProvider(OidcProviderKind.GITHUB))
                     .compose(config -> {
                         if (config == null) {
                             return Future.failedFuture(new IllegalStateException(
                                     "No enabled GitHub OAuth configuration exists to verify the install."));
                         }
                         return Future.fromCompletionStage(secretReferenceResolver.resolve(config.getSecretNameRef()))
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
        String appId = properties.getGithub().getAppId();
        // The app_id filter makes a mis-provisioned credential fail closed: if the
        // github-platform row ever names an OAuth client that is not this App's, the
        // listed installations cannot vouch for installs this platform mints tokens on.
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
        return Future.fromCompletionStage(saveSync(install));
    }

    @Override
    public CompletableFuture<GitHubAppInstallation> findForCurrentOrg() {
        return installationRepository.findAll(requireOrganizationId(), Pageable.ofSize(1))
                                     .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    @Override
    public CompletableFuture<GitHubAppInstallation> findByGithubInstallationId(long githubInstallationId) {
        return installationRepository.findByGithubInstallationId(githubInstallationId, requireOrganizationId());
    }
}
