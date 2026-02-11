package org.kinotic.structures.internal.endpoints.openapi;

import io.vertx.core.Completable;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import org.kinotic.structures.internal.utils.VertxWebUtil;

/**
 * Created by Navíd Mitchell 🤪on 6/22/23.
 */
@RequiredArgsConstructor
class NoValueHandler implements Completable<Void> {

    private final RoutingContext context;

    @Override
    public void complete(Void aVoid, Throwable throwable) {
        if(throwable != null){
            VertxWebUtil.writeException(context, throwable);
        }else {
            context.response().setStatusCode(200);
            context.response().end();
        }
    }
}
