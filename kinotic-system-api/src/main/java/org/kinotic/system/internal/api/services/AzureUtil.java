package org.kinotic.system.internal.api.services;

import com.azure.core.management.exception.ManagementException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletionException;

/**
 * What every Azure-backed provisioner needs from the SDK: the management plane's failures
 * classified by HTTP status, and its reactive results carried onto the caller's Vert.x context.
 */
public final class AzureUtil {

    private AzureUtil() {
    }

    /**
     * Whether the failure says another write is still in flight on the resource, so the same
     * write succeeds once that one has completed.
     */
    public static boolean isConflict(Throwable error) {
        return hasStatus(error, 409);
    }

    /**
     * The resource the lookup emits, or nothing when it does not exist.
     */
    public static <T> Mono<T> emptyIfNotFound(Mono<T> lookup) {
        return lookup.onErrorResume(error -> hasStatus(error, 404), error -> Mono.empty());
    }

    /**
     * Bridges an SDK result onto the current Vert.x context, so everything composed after it
     * runs where the caller's context-locals are visible.
     */
    public static <T> Future<T> toFuture(Mono<T> mono, Vertx vertx) {
        return Future.fromCompletionStage(mono.toFuture(), vertx.getOrCreateContext());
    }

    private static boolean hasStatus(Throwable error, int status) {
        // a failure that crossed a CompletableFuture arrives wrapped
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause instanceof ManagementException management
                && management.getResponse() != null
                && management.getResponse().getStatusCode() == status;
    }

}
