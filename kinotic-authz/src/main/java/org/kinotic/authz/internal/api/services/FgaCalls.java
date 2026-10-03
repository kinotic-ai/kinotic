package org.kinotic.authz.internal.api.services;

import dev.openfga.sdk.api.client.ApiResponse;
import io.vertx.core.Future;
import org.kinotic.core.api.utils.KinoticUtil;


/**
 * Bridges the OpenFGA client's calls into Vert.x futures on the caller's context.
 */
final class FgaCalls {

    private FgaCalls() {
    }

    /**
     * Starts the call and yields its response's data.
     */
    static <T> Future<T> data(FgaCall<T> call) {
        Future<T> ret;
        try {
            ret = KinoticUtil.toFuture(call.start()).map(ApiResponse::getData);
        } catch (Exception e) {
            ret = Future.failedFuture(e);
        }
        return ret;
    }

}
