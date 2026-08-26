package org.kinotic.management.internal.api.services.github;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.secret.SecretReferenceResolver;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.OidcProviderKind;
import org.kinotic.domain.api.model.security.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.model.security.OrganizationParticipant;
import org.kinotic.domain.api.services.security.OrgSignupOidcConfigurationService;
import org.kinotic.management.api.config.github.GithubProperties;
import org.kinotic.management.api.model.GitHubAppInstallation;
import org.kinotic.management.api.model.GitHubInstallCompletion;
import org.kinotic.management.internal.api.repositories.GitHubAppInstallationRepository;
import org.kinotic.management.internal.api.services.github.client.GitHubApiClient;
import org.kinotic.management.internal.api.services.github.client.InstallationDetails;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultGitHubAppInstallationServiceTest {

    private static final String CALLER_ORG = "org-1";
    private static final String APP_ID = "777";
    private static final long INSTALLATION_ID = 55L;

    private GitHubAppInstallationRepository repository;
    private GitHubInstallStateService stateService;
    private GitHubApiClient apiClient;
    private DefaultGitHubAppInstallationService service;

    @BeforeEach
    void setUp() {
        repository = mock(GitHubAppInstallationRepository.class);
        when(repository.getType()).thenReturn(GitHubAppInstallation.class);
        when(repository.saveSync(any(GitHubAppInstallation.class), anyString()))
                .thenAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));
        // Org not yet linked unless a test overrides this — completeInstall consults
        // findForCurrentOrg (a findAll under the hood) before verifying ownership
        when(repository.findAll(eq(CALLER_ORG), any(Pageable.class)))
                .thenReturn(Future.succeededFuture(new Page<>(List.of(), 0L)));

        OrganizationParticipant participant = mock(OrganizationParticipant.class);
        when(participant.getOrganizationId()).thenReturn(CALLER_ORG);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.requireParticipant(OrganizationParticipant.class)).thenReturn(participant);

        GithubProperties githubProperties = new GithubProperties().setAppId(APP_ID);

        // Real state store (Caffeine path) so staging + single-use consumption are exercised
        stateService = new GitHubInstallStateService(null);
        stateService.start();

        OrgSignupOidcConfiguration credential = new OrgSignupOidcConfiguration();
        credential.setClientId("client-id");
        credential.setSecretNameRef("github-platform");
        OrgSignupOidcConfigurationService oidcConfigurationService = mock(OrgSignupOidcConfigurationService.class);
        when(oidcConfigurationService.findEnabledByProvider(OidcProviderKind.GITHUB))
                .thenReturn(Future.succeededFuture(credential));

        SecretReferenceResolver secretReferenceResolver = mock(SecretReferenceResolver.class);
        when(secretReferenceResolver.resolve("github-platform"))
                .thenReturn(Future.succeededFuture("client-secret"));

        apiClient = mock(GitHubApiClient.class);
        when(apiClient.exchangeUserAccessCode("client-id", "client-secret", "good-code"))
                .thenReturn(Future.succeededFuture("user-token"));

        service = new DefaultGitHubAppInstallationService(repository,
                                                          securityContext,
                                                          githubProperties,
                                                          stateService,
                                                          apiClient,
                                                          oidcConfigurationService,
                                                          secretReferenceResolver);
    }

    @Test
    void bindsAnInstallationTheAuthorizingUserControls() {
        when(apiClient.listUserInstallations("user-token"))
                .thenReturn(Future.succeededFuture(List.of(
                        new InstallationDetails(11L, 777L, "personal", "User"),
                        new InstallationDetails(INSTALLATION_ID, 777L, "acme", "Organization"))));
        String state = stage(CALLER_ORG, "/projects?openNewProject=1");

        GitHubInstallCompletion completion = service.completeInstall(INSTALLATION_ID, state, "good-code").await();

        assertEquals("/projects?openNewProject=1", completion.getReturnTo());
        ArgumentCaptor<GitHubAppInstallation> captor = ArgumentCaptor.forClass(GitHubAppInstallation.class);
        verify(repository).saveSync(captor.capture(), eq(CALLER_ORG));
        GitHubAppInstallation saved = captor.getValue();
        assertEquals(CALLER_ORG, saved.getOrganizationId());
        assertEquals(INSTALLATION_ID, saved.getGithubInstallationId().longValue());
        assertEquals("acme", saved.getAccountLogin());
        assertEquals("Organization", saved.getAccountType());
    }

    @Test
    void rejectsAnInstallationTheAuthorizingUserCannotAccess() {
        // Another customer's live installation id: resolvable by the App, but absent
        // from the authorizing user's /user/installations list
        when(apiClient.listUserInstallations("user-token"))
                .thenReturn(Future.succeededFuture(List.of(
                        new InstallationDetails(11L, 777L, "personal", "User"))));
        String state = stage(CALLER_ORG, null);

        assertFailsWith(AuthorizationException.class,
                        service.completeInstall(INSTALLATION_ID, state, "good-code"));
        verify(repository, never()).saveSync(any(), anyString());
    }

    @Test
    void rejectsAnInstallationOfAnotherApp() {
        when(apiClient.listUserInstallations("user-token"))
                .thenReturn(Future.succeededFuture(List.of(
                        new InstallationDetails(INSTALLATION_ID, 888L, "acme", "Organization"))));
        String state = stage(CALLER_ORG, null);

        assertFailsWith(AuthorizationException.class,
                        service.completeInstall(INSTALLATION_ID, state, "good-code"));
        verify(repository, never()).saveSync(any(), anyString());
    }

    @Test
    void rejectsBindingASecondInstallationWhileLinked() {
        GitHubAppInstallation existing = new GitHubAppInstallation();
        existing.setGithubInstallationId(66L);
        when(repository.findAll(eq(CALLER_ORG), any(Pageable.class)))
                .thenReturn(Future.succeededFuture(new Page<>(List.of(existing), 1L)));
        String state = stage(CALLER_ORG, null);

        assertFailsWith(IllegalStateException.class,
                        service.completeInstall(INSTALLATION_ID, state, "good-code"));
        verifyNoInteractions(apiClient);
        verify(repository, never()).saveSync(any(), anyString());
    }

    @Test
    void refreshesTheInstallationAlreadyBoundAfterReverifying() {
        GitHubAppInstallation existing = new GitHubAppInstallation();
        existing.setGithubInstallationId(INSTALLATION_ID);
        when(repository.findAll(eq(CALLER_ORG), any(Pageable.class)))
                .thenReturn(Future.succeededFuture(new Page<>(List.of(existing), 1L)));
        when(apiClient.listUserInstallations("user-token"))
                .thenReturn(Future.succeededFuture(List.of(
                        new InstallationDetails(INSTALLATION_ID, 777L, "acme", "Organization"))));
        String state = stage(CALLER_ORG, null);

        service.completeInstall(INSTALLATION_ID, state, "good-code").await();

        // The refresh path still demands the ownership proof before re-persisting
        verify(apiClient).listUserInstallations("user-token");
        verify(repository).saveSync(any(GitHubAppInstallation.class), eq(CALLER_ORG));
    }

    @Test
    void rejectsAMissingUserAuthorizationCode() {
        String state = stage(CALLER_ORG, null);

        assertFailsWith(IllegalStateException.class,
                        service.completeInstall(INSTALLATION_ID, state, null));
        verifyNoInteractions(apiClient);
        verify(repository, never()).saveSync(any(), anyString());
    }

    @Test
    void rejectsAnUnknownOrConsumedState() {
        assertFailsWith(IllegalStateException.class,
                        service.completeInstall(INSTALLATION_ID, "never-staged", "good-code"));
        verifyNoInteractions(apiClient);
        verify(repository, never()).saveSync(any(), anyString());
    }

    @Test
    void rejectsAStateStagedForAnotherOrganization() {
        String state = stage("org-2", null);

        assertFailsWith(AuthorizationException.class,
                        service.completeInstall(INSTALLATION_ID, state, "good-code"));
        verifyNoInteractions(apiClient);
        verify(repository, never()).saveSync(any(), anyString());
    }

    private String stage(String orgId, String returnTo) {
        return stateService.stage(new StagedInstall()
                                          .setOrganizationId(orgId)
                                          .setReturnTo(returnTo));
    }

    private static void assertFailsWith(Class<? extends Throwable> expected,
                                        Future<GitHubInstallCompletion> future) {
        assertThrows(expected, future::await);
    }
}
