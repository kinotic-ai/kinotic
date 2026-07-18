package org.kinotic.persistence.internal.endpoints.openapi;

import io.vertx.core.Completable;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import org.kinotic.gateway.api.utils.ApiGatewayUtil;

/**
 * Created by Navíd Mitchell 🤪on 6/22/23.
 */
@RequiredArgsConstructor
class NoValueHandler implements Completable<Void> {

    private final RoutingContext context;

    @Override
    public void complete(Void aVoid, Throwable throwable) {
        if(throwable != null){
            ApiGatewayUtil.writeException(context, throwable);
        }else {
            context.response().setStatusCode(200);
            context.response().end();
        }
    }
}
