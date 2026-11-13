package org.kinotic.core.api.security;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.exceptions.AuthenticationException;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs {@link AuthenticationHandler} in front of a route on a real server: rejected credentials answer 401, a security
 * service that could not answer 503, and an authenticated request reaches the route as its participant.
 *
 * Created by Navíd Mitchell 🤪 on 11/8/26.
 */
public class AuthenticationHandlerTests {

    private static Vertx vertx;
    private static HttpServer server;
    // held for the whole run: Vert.x closes a client nothing references once it is collected, which fails a
    // request still in flight on it
    private static HttpClient client;
    private static SecurityService securityService;
    private static SecurityContext securityContext;

    @BeforeAll
    public static void start() throws Exception {
        vertx = Vertx.vertx();
        securityService = mock(SecurityService.class);
        securityContext = mock(SecurityContext.class);
        Router router = Router.router(vertx);
        router.route().handler(new AuthenticationHandler(securityService, securityContext, new AuthenticationContext(AuthenticationContext.PUBLISHED_SERVICES)));
        router.route().handler(ctx -> ctx.response().setStatusCode(204).end());
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
    public void testRejectedCredentialsAnswerUnauthorized() throws Exception {
        when(securityService.authenticate(any(), any())).thenReturn(Future.failedFuture(new AuthenticationException("Invalid credentials")));
        Assertions.assertEquals(401, status());
    }

    @Test
    public void testASecurityServiceThatCannotAnswerAnswersServiceUnavailable() throws Exception {
        when(securityService.authenticate(any(), any())).thenReturn(Future.failedFuture(new IllegalStateException("store down")));
        Assertions.assertEquals(503, status());
    }

    @Test
    public void testAnAuthenticatedRequestReachesTheRouteAsItsParticipant() throws Exception {
        Participant participant = mock(Participant.class);
        when(securityService.authenticate(any(), any())).thenReturn(Future.succeededFuture(participant));
        Assertions.assertEquals(204, status());
        verify(securityContext).setParticipant(any(), same(participant));
    }

    private static int status() throws Exception {
        return client.request(HttpMethod.GET, server.actualPort(), "127.0.0.1", "/")
                     .compose(HttpClientRequest::send)
                     .map(HttpClientResponse::statusCode)
                     .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
