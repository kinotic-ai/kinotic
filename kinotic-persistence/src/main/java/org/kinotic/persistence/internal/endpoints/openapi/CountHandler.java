package org.kinotic.persistence.internal.endpoints.openapi;

import io.vertx.core.Completable;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import org.kinotic.gateway.api.utils.ApiGatewayUtil;

/**
 * Created by Navíd Mitchell 🤪 on 5/2/24.
 */
@RequiredArgsConstructor
public class CountHandler implements Completable<Long> {

    private final RoutingContext context;

    @Override
    public void complete(Long value, Throwable throwable) {
        if (throwable == null) {
            context.response().putHeader("Content-Type", "application/json");
            context.response().setStatusCode(200);
            context.response().end(Buffer.buffer("{ \"count\": " + value.toString() + '}'));
        } else {
            ApiGatewayUtil.writeException(context, throwable);
        }
    }
}
