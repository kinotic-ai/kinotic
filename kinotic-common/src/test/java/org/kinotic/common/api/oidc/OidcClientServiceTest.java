package org.kinotic.common.api.oidc;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kinotic.common_autoconfig.KinoticCommonAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Signs in through {@link FakeIdentityProvider} with the {@link OidcClientService} the module auto-configures.
 */
class OidcClientServiceTest {

    private static final String CALLBACK_URL = "https://app.example.com/callback";
    private static final String CLIENT_SECRET = "test-secret";

    private static Vertx vertx;
    private static FakeIdentityProvider provider;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KinoticCommonAutoConfiguration.class))
            .withBean(Vertx.class, () -> vertx);

    @BeforeAll
    static void startProvider() throws Exception {
        vertx = Vertx.vertx();
        provider = new FakeIdentityProvider(vertx);
    }

    @AfterAll
    static void stopProvider() throws Exception {
        provider.close();
        await(vertx.close());
    }

    @Test
    void discoveredProviderSignsInWithItsIdToken() {
        contextRunner.run(context -> {
            OidcClient client = await(context.getBean(OidcClientService.class).createClient(discoveredSettings(), CLIENT_SECRET));
            try {
                OidcAuthorization authorization = OidcAuthorization.create();
                String code = provider.approve(client.authorizationUrl(authorization, CALLBACK_URL, List.of("openid", "email")));

                assertEquals(authorization.state(), provider.authorizationParameter("state"));
                assertEquals(authorization.nonce(), provider.authorizationParameter("nonce"));
                assertEquals("S256", provider.authorizationParameter("code_challenge_method"));
                assertEquals("openid email", provider.authorizationParameter("scope"));

                Map<String, Object> claims = await(client.verifiedClaims(code, CALLBACK_URL, authorization));
                assertEquals(FakeIdentityProvider.SUBJECT, claims.get("sub"));
                assertEquals(FakeIdentityProvider.EMAIL, claims.get("email"));
                assertTrue(OAuth2Util.isEmailVerified(claims, OidcProviderType.OIDC));
            } finally {
                client.close();
            }
        });
    }

    @Test
    void idTokenOfAnotherSignInIsRejected() {
        contextRunner.run(context -> {
            OidcClient client = await(context.getBean(OidcClientService.class).createClient(discoveredSettings(), CLIENT_SECRET));
            try {
                OidcAuthorization authorization = OidcAuthorization.create();
                String code = provider.approve(client.authorizationUrl(authorization, CALLBACK_URL, List.of("openid")));

                ExecutionException failure = assertThrows(ExecutionException.class, () -> await(
                        client.verifiedClaims(code, CALLBACK_URL, new OidcAuthorization(
                                authorization.state(), "the-nonce-of-another-sign-in", authorization.pkceVerifier()))));
                assertInstanceOf(InvalidIdTokenException.class, failure.getCause());
            } finally {
                client.close();
            }
        });
    }

    @Test
    void expiredIdTokenIsRejected() {
        // the token response still states a valid lifetime, so only the id token's own exp can refuse it
        assertIdTokenRejected(-600L);
    }

    @Test
    void idTokenStatingNoExpiryIsRejected() {
        assertIdTokenRejected(null);
    }

    @Test
    void codeIsRedeemedOnlyWithItsPkceVerifier() {
        contextRunner.run(context -> {
            OidcClient client = await(context.getBean(OidcClientService.class).createClient(discoveredSettings(), CLIENT_SECRET));
            try {
                OidcAuthorization authorization = OidcAuthorization.create();
                String code = provider.approve(client.authorizationUrl(authorization, CALLBACK_URL, List.of("openid")));

                assertThrows(ExecutionException.class, () -> await(
                        client.verifiedClaims(code, CALLBACK_URL, new OidcAuthorization(
                                authorization.state(), authorization.nonce(), "another-verifier"))));
            } finally {
                client.close();
            }
        });
    }

    @Test
    void providerWithNoIdTokenSignsInWithItsProfileAndBestEmail() {
        contextRunner.run(context -> {
            OidcClientSettings settings = new TestOidcClientSettings()
                    .setProvider(OidcProviderType.GITHUB)
                    .setAuthority(provider.baseUrl())
                    .setClientId(FakeIdentityProvider.CLIENT_ID)
                    .setAuthorizationUri(provider.baseUrl() + "/login/oauth/authorize")
                    .setTokenUri(provider.baseUrl() + "/login/oauth/access_token")
                    .setUserInfoUri(provider.baseUrl() + "/user")
                    .setUserEmailsUri(provider.baseUrl() + "/user/emails");
            OidcClient client = await(context.getBean(OidcClientService.class).createClient(settings, CLIENT_SECRET));
            try {
                OidcAuthorization authorization = OidcAuthorization.create();
                String url = client.authorizationUrl(authorization, CALLBACK_URL, List.of("read:user", "user:email"));
                assertTrue(url.startsWith(provider.baseUrl() + "/login/oauth/authorize?"));
                String code = provider.approve(url);

                Map<String, Object> claims = await(client.verifiedClaims(code, CALLBACK_URL, authorization));
                assertEquals("42", claims.get("sub"));
                assertEquals("The Octocat", claims.get("name"));
                assertEquals(FakeIdentityProvider.EMAIL, claims.get("email"));
                assertEquals(Boolean.TRUE, claims.get("email_verified"));
            } finally {
                client.close();
            }
        });
    }

    @Test
    void settingsThatNameOnlyOneEndpointAreRefused() {
        contextRunner.run(context -> {
            OidcClientSettings settings = discoveredSettings().setTokenUri(provider.baseUrl() + "/token");
            ExecutionException failure = assertThrows(ExecutionException.class, () -> await(
                    context.getBean(OidcClientService.class).createClient(settings, CLIENT_SECRET)));
            assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        });
    }

    private void assertIdTokenRejected(Long idTokenLifetimeSeconds) {
        contextRunner.run(context -> {
            OidcClient client = await(context.getBean(OidcClientService.class).createClient(discoveredSettings(), CLIENT_SECRET));
            provider.idTokenLifetimeSeconds(idTokenLifetimeSeconds);
            try {
                OidcAuthorization authorization = OidcAuthorization.create();
                String code = provider.approve(client.authorizationUrl(authorization, CALLBACK_URL, List.of("openid")));

                ExecutionException failure = assertThrows(ExecutionException.class, () -> await(
                        client.verifiedClaims(code, CALLBACK_URL, authorization)));
                assertInstanceOf(InvalidIdTokenException.class, failure.getCause());
            } finally {
                provider.idTokenLifetimeSeconds(300L);
                client.close();
            }
        });
    }

    private static TestOidcClientSettings discoveredSettings() {
        return new TestOidcClientSettings()
                .setProvider(OidcProviderType.OIDC)
                .setAuthority(provider.baseUrl())
                .setClientId(FakeIdentityProvider.CLIENT_ID);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }

}
