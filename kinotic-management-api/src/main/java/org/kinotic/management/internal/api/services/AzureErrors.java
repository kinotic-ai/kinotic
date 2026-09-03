package org.kinotic.management.internal.api.services;

import com.azure.core.management.exception.ManagementException;

import java.util.concurrent.CompletionException;

/**
 * Classifies the failures Azure's management plane answers with, by HTTP status, so a
 * provisioner can treat "not there yet" and "busy" differently from a real failure.
 */
public final class AzureErrors {

    private AzureErrors() {
    }

    /**
     * Whether the failure says the resource does not exist.
     */
    public static boolean isNotFound(Throwable error) {
        return hasStatus(error, 404);
    }

    /**
     * Whether the failure says another write is still in flight on the resource, so the same
     * write succeeds once that one has completed.
     */
    public static boolean isConflict(Throwable error) {
        return hasStatus(error, 409);
    }

    private static boolean hasStatus(Throwable error, int status) {
        // a failure that crossed a CompletableFuture arrives wrapped
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause instanceof ManagementException management
                && management.getResponse() != null
                && management.getResponse().getStatusCode() == status;
    }

}
