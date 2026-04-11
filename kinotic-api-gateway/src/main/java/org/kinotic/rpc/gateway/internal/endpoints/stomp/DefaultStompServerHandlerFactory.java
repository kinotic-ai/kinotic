

package org.kinotic.rpc.gateway.internal.endpoints.stomp;

import io.vertx.core.Vertx;
import io.vertx.ext.stomp.lite.StompServerConnection;
import io.vertx.ext.stomp.lite.StompServerHandler;
import io.vertx.ext.stomp.lite.StompServerHandlerFactory;
import org.kinotic.rpc.gateway.internal.endpoints.Services;
import org.springframework.stereotype.Component;

/**
 *
 * Created by Navid Mitchell on 2019-02-04.
 */
@Component
public class DefaultStompServerHandlerFactory implements StompServerHandlerFactory {

    private final Vertx vertx;
    private final Services services;

    public DefaultStompServerHandlerFactory(Vertx vertx, Services services) {
        this.vertx = vertx;
        this.services = services;
    }

    @Override
    public StompServerHandler create(StompServerConnection stompServerConnection) {
        return new DefaultStompServerHandler(vertx,
                                             services,
                                             stompServerConnection);
    }

}
