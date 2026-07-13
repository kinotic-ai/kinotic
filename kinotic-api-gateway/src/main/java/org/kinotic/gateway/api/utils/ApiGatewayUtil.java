package org.kinotic.gateway.api.utils;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.gateway.api.config.CorsProperties;
import org.kinotic.gateway.api.config.SslProperties;

import java.util.Set;
import java.util.concurrent.CompletionException;

/**
 * Helpers for the Vert.x HTTP servers and routers fronted by the api-gateway: CORS handling,
 * PEM-based TLS, router assembly, and rendering exceptions as JSON error responses. Applied to
 * every browser-facing route (STOMP, static web, and the openapi/graphql routes mounted by
 * other modules).
 */
@Slf4j
public final class ApiGatewayUtil {

    /**
     * The HTTP methods kinotic's HTTP routes accept. Pinned here rather than exposed on
     * {@link CorsProperties} because Vert.x's {@code CorsHandler} allows no methods by default
     * and a misconfigured deployment value would silently break every non-GET endpoint. Update
     * this set if a new HTTP verb is introduced anywhere in the codebase.
     */
    private static final Set<HttpMethod> ALLOWED_METHODS = Set.of(HttpMethod.GET,
                                                                  HttpMethod.POST,
                                                                  HttpMethod.PUT,
                                                                  HttpMethod.PATCH,
                                                                  HttpMethod.DELETE,
                                                                  HttpMethod.OPTIONS);

    private ApiGatewayUtil() {}

    public static CorsHandler createCorsHandler(CorsProperties properties) {
        String pattern = properties.getAllowedOriginPattern();
        if ("*".equals(pattern)) {
            pattern = ".*";
        }
        CorsHandler corsHandler = CorsHandler.create()
                                             .addOriginWithRegex(pattern)
                                             .allowedMethods(ALLOWED_METHODS)
                                             .allowedHeaders(properties.getAllowedHeaders());
        if (properties.getAllowCredentials() != null) {
            corsHandler.allowCredentials(properties.getAllowCredentials());
        }
        return corsHandler;
    }

    /**
     * If SSL is enabled in the given properties, configures the server options
     * with PEM-based TLS. Otherwise leaves the options unchanged.
     *
     * @param options the server options to configure
     * @param ssl     the SSL properties
     * @return the same options instance for chaining
     */
    public static HttpServerOptions applySsl(HttpServerOptions options, SslProperties ssl) {
        if (ssl != null && ssl.isEnabled()) {
            options.setSsl(true)
                   .setKeyCertOptions(new PemKeyCertOptions()
                           .setCertPath(ssl.getCertPath())
                           .setKeyPath(ssl.getKeyPath()));
        }
        return options;
    }

    /**
     * Creates a router pre-wired with CORS and the exception-converting failure handler, so
     * unhandled failures render as JSON with a mapped status code instead of Vert.x's default
     * bare reason-phrase response.
     */
    public static Router createRouterWithCors(Vertx vertx, CorsProperties corsProperties) {
        Router router = Router.router(vertx);
        router.route().failureHandler(createExceptionConvertingFailureHandler());
        router.route().handler(createCorsHandler(corsProperties));
        return router;
    }

    public static Handler<RoutingContext> createExceptionConvertingFailureHandler(){
        return ctx -> {
            writeException(ctx, ctx.failure());
        };
    }

    public static void writeException(RoutingContext context, Throwable throwable){
        HttpServerResponse response = context.response();
        String errorMessage;
        int statusCode = context.statusCode();

        if(throwable != null){

            if(throwable instanceof CompletionException){
                if(throwable.getCause() != null) {
                    throwable = throwable.getCause();
                }
            }

            errorMessage = throwable.getMessage();

            if(statusCode == -1){
                if (throwable instanceof IllegalArgumentException) {
                    statusCode = 400;
                } else if (throwable instanceof NullPointerException) {
                    statusCode = 400;
                } else if(throwable instanceof AuthenticationException){
                    statusCode = 401;
                } else if (throwable instanceof AuthorizationException) {
                    statusCode = 403;
                } else {
                    statusCode = 500;
                }
            }
            log.debug("Error processing web request. Status Code ({})", statusCode, throwable);
        }else{

            if(statusCode == -1){
                statusCode = 500;
            }
            errorMessage = "Server Error";

            log.warn("Unknown exception occurred processing web request. Status Code ({})", statusCode);
        }
        String jsonString = new JsonObject().put("error", errorMessage).encode();
        response.setStatusCode(statusCode);
        response.putHeader("Content-Type", "application/json");
        response.end(jsonString);
    }
}
