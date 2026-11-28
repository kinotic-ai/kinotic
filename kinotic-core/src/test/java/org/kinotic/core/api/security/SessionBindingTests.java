package org.kinotic.core.api.security;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.exceptions.AuthenticationException;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs {@link SessionBinding#handler} behind a session handler on a real server: a page's login is read as the
 * participant its identity acts as at the time of each request, ends for good once the identity may no longer act,
 * and survives a security service that could not answer, and a session whose last login ends is ended with its
 * cookie, whatever activity a connection recorded in it.
 */
public class SessionBindingTests {

    private static final String PARTICIPANT_HEADER = "participant";

    private static Vertx vertx;
    private static HttpServer server;
    // held for the whole run: Vert.x closes a client nothing references once it is collected, which fails a
    // request still in flight on it
    private static HttpClient client;
    private static SecurityService securityService;

    @BeforeAll
    public static void start() throws Exception {
        vertx = Vertx.vertx();
        securityService = mock(SecurityService.class);
        SessionHandler sessionHandler = SessionHandler.create(LocalSessionStore.create(vertx));
        Router router = Router.router(vertx);
        router.route().handler(sessionHandler).handler(SessionBinding.handler("vertx-web.session", securityService));
        router.post("/login").handler(ctx -> {
            ConnectedInfo login = new ConnectedInfo();
            login.setParticipant(participant("signed-in"));
            SessionBinding.bind(ctx, SessionBinding.origin(ctx), login);
            ctx.response().setStatusCode(204).end();
        });
        router.post("/activity").handler(ctx -> {
            SessionBinding.recordActivity(ctx.session(), System.currentTimeMillis());
            ctx.response().setStatusCode(204).end();
        });
        router.post("/logout").handler(ctx -> {
            SessionBinding.unbind(ctx);
            ctx.response().setStatusCode(204).end();
        });
        router.get("/me").handler(ctx -> {
            ConnectedInfo login = SessionBinding.connectedInfo(ctx);
            if (login == null) {
                ctx.response().setStatusCode(401).end();
            } else {
                ctx.response().putHeader(PARTICIPANT_HEADER, login.getParticipant().getId()).setStatusCode(204).end();
            }
        });
        server = vertx.createHttpServer()
                      .requestHandler(router)
                      .listen(0, "127.0.0.1")
                      .toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
        client = vertx.createHttpClient();
    }

    @AfterAll
    public static void stop() throws Exception {
        vertx.close().toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    @Test
    public void testALoginIsReadAsTheParticipantItsIdentityActsAsAtTheRequest() throws Exception {
        String cookie = login();
        Participant current = participant("current");
        when(securityService.reauthenticate(any())).thenReturn(Future.succeededFuture(current));

        HttpClientResponse response = me(cookie);

        Assertions.assertEquals(204, response.statusCode());
        Assertions.assertEquals("current", response.getHeader(PARTICIPANT_HEADER));
    }

    @Test
    public void testALoginEndsForGoodOnceItsIdentityMayNoLongerAct() throws Exception {
        String cookie = login();
        when(securityService.reauthenticate(any())).thenReturn(Future.failedFuture(new AuthenticationException("The identity was removed")));
        Assertions.assertEquals(401, me(cookie).statusCode());

        // the login was removed from the session, so an identity that may act again does not bring it back
        Participant current = participant("current");
        when(securityService.reauthenticate(any())).thenReturn(Future.succeededFuture(current));
        Assertions.assertEquals(401, me(cookie).statusCode());
    }

    @Test
    public void testASecurityServiceThatCannotAnswerAnswersServiceUnavailableAndKeepsTheLogin() throws Exception {
        String cookie = login();
        when(securityService.reauthenticate(any())).thenReturn(Future.failedFuture(new IllegalStateException("store down")));
        Assertions.assertEquals(503, me(cookie).statusCode());

        Participant current = participant("current");
        when(securityService.reauthenticate(any())).thenReturn(Future.succeededFuture(current));
        Assertions.assertEquals(204, me(cookie).statusCode());
    }

    @Test
    public void testTheLastLogoutEndsASessionThatHoldsRecordedActivity() throws Exception {
        String cookie = login();
        Participant current = participant("signed-in");
        when(securityService.reauthenticate(any())).thenReturn(Future.succeededFuture(current));
        Assertions.assertEquals(204, post("/activity", cookie).statusCode());

        HttpClientResponse logout = post("/logout", cookie);

        Assertions.assertEquals(204, logout.statusCode());
        String cleared = logout.getHeader(HttpHeaders.SET_COOKIE);
        Assertions.assertNotNull(cleared, "the logout left the session cookie in place");
        Assertions.assertTrue(cleared.contains("Max-Age=0"), "the logout did not end the session cookie: " + cleared);
    }

    // signs a page in and returns the session cookie the server issued it
    private static String login() throws Exception {
        HttpClientResponse response = client.request(HttpMethod.POST, server.actualPort(), "127.0.0.1", "/login")
                                            .compose(request -> request.send())
                                            .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        Assertions.assertEquals(204, response.statusCode());
        String issued = response.getHeader(HttpHeaders.SET_COOKIE);
        return issued.substring(0, issued.indexOf(';'));
    }

    private static HttpClientResponse post(String path, String cookie) throws Exception {
        return client.request(HttpMethod.POST, server.actualPort(), "127.0.0.1", path)
                     .compose(request -> request.putHeader(HttpHeaders.COOKIE, cookie).send())
                     .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private static HttpClientResponse me(String cookie) throws Exception {
        return client.request(HttpMethod.GET, server.actualPort(), "127.0.0.1", "/me")
                     .compose(request -> request.putHeader(HttpHeaders.COOKIE, cookie).send())
                     .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private static Participant participant(String id) {
        Participant ret = mock(Participant.class);
        when(ret.getId()).thenReturn(id);
        return ret;
    }
}
