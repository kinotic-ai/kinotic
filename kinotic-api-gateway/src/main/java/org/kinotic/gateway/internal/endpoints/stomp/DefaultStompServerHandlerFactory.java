

package org.kinotic.gateway.internal.endpoints.stomp;

import io.vertx.ext.stomp.lite.StompServerHandler;
import io.vertx.ext.stomp.lite.StompServerHandlerFactory;
import org.kinotic.gateway.internal.endpoints.Services;
import org.springframework.stereotype.Component;

/**
 *
 * Created by Navid Mitchell on 2019-02-04.
 */
@Component
public class DefaultStompServerHandlerFactory implements StompServerHandlerFactory {

    private final Services services;

    public DefaultStompServerHandlerFactory(Services services) {
        this.services = services;
    }

    @Override
    public StompServerHandler create() {
        return new DefaultStompServerHandler(services);
    }

}
