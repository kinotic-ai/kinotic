package org.kinotic.authz.internal.api.services;

import dev.openfga.sdk.api.client.ApiResponse;

import java.util.concurrent.CompletableFuture;

/**
 * One call of the OpenFGA client, started lazily so a failure to build or send the request is observed on
 * the resulting future like any other.
 */
interface FgaCall<T> {

    CompletableFuture<ApiResponse<T>> start() throws Exception;

}
