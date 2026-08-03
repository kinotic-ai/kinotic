package org.kinotic.core.api.security;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import org.kinotic.core.api.event.EventConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * Vertx Authentication handler that uses the {@link SecurityService} to authenticate requests.
 * Created by Navíd Mitchell 🤪on 6/19/23.
 */
public class AuthenticationHandler implements Handler<RoutingContext> {

    private final SecurityService securityService;
    private final SecurityContext securityContext;

    public AuthenticationHandler(SecurityService securityService,
                                 SecurityContext securityContext) {
        this.securityService = securityService;
        this.securityContext = securityContext;
    }

    @Override
    public void handle(RoutingContext ctx) {
        // must pause receiving of content during auth, otherwise the body handler must be before auth which seems baaad!
        ctx.request().pause();

        Map<String, String> authInfo = new HashMap<>(ctx.request().headers().size());
        for(Map.Entry<String, String> entry : ctx.request().headers()){
            authInfo.put(entry.getKey().toLowerCase(), entry.getValue());
        }

        securityService.authenticate(authInfo)
                       .onComplete(event -> {
                           if(event.succeeded()){
                               // RoutingContext stash read only by the OpenAPI/GraphQL
                               // RoutingContextToEntityContextAdapter; every other consumer reads
                               // the SecurityContext binding below
                               ctx.put(EventConstants.SENDER_HEADER, event.result());
                               // Bind the Participant to the current Vert.x context so downstream
                               // handlers (and anything they call) can read it via
                               // SecurityContext.currentParticipant().
                               Context vertxContext = Vertx.currentContext();
                               if (vertxContext != null) {
                                   securityContext.setParticipant(vertxContext, event.result());
                               }
                               ctx.request().resume();
                               ctx.next();
                           }else{
                               ctx.request().resume();
                               ctx.fail(401, event.cause());
                           }
                       });
    }
}
