package org.kinotic.core.api.security;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.exceptions.AuthenticationException;

import java.util.HashMap;
import java.util.Map;

/**
 * Vertx Authentication handler that uses the {@link SecurityService} to authenticate requests, in the
 * {@link AuthenticationContext} the route serves. Rejected credentials fail the request with a 401, and a security
 * service that could not answer fails it with a 503.
 * Created by Navíd Mitchell 🤪on 6/19/23.
 */
@Slf4j
public class AuthenticationHandler implements Handler<RoutingContext> {

    private final SecurityService securityService;
    private final SecurityContext securityContext;
    private final AuthenticationContext context;

    public AuthenticationHandler(SecurityService securityService,
                                 SecurityContext securityContext,
                                 AuthenticationContext context) {
        this.securityService = securityService;
        this.securityContext = securityContext;
        this.context = context;
    }

    @Override
    public void handle(RoutingContext ctx) {
        // must pause receiving of content during auth, otherwise the body handler must be before auth which seems baaad!
        ctx.request().pause();

        Map<String, String> authInfo = new HashMap<>(ctx.request().headers().size());
        for(Map.Entry<String, String> entry : ctx.request().headers()){
            authInfo.put(entry.getKey().toLowerCase(), entry.getValue());
        }

        securityService.authenticate(authInfo, context)
                       .onComplete(event -> {
                           if(event.succeeded()){
                               // Exposes the Participant to route handlers that read it from the RoutingContext
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
                               // rejected credentials are the caller's to change; a security service that could not
                               // answer is an outage the caller should retry through
                               if (event.cause() instanceof AuthenticationException) {
                                   ctx.fail(401, event.cause());
                               } else {
                                   log.warn("The security service could not authenticate a request", event.cause());
                                   ctx.fail(503, event.cause());
                               }
                           }
                       });
    }
}
