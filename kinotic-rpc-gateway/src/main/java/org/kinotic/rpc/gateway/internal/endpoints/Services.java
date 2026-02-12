

package org.kinotic.rpc.gateway.internal.endpoints;

import io.vertx.core.Vertx;
import org.kinotic.rpc.api.Continuum;
import org.kinotic.rpc.api.config.KinoticRpcProperties;
import org.kinotic.rpc.api.security.SecurityService;
import org.kinotic.rpc.api.event.EventBusService;
import org.kinotic.rpc.api.event.EventStreamService;
import org.kinotic.rpc.api.security.SessionManager;
import org.kinotic.rpc.gateway.api.config.ContinuumGatewayProperties;
import org.kinotic.rpc.gateway.internal.endpoints.stomp.DefaultStompServerHandler;
import org.kinotic.rpc.internal.api.service.ExceptionConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Facade class to make it easier to get needed services into {@link DefaultStompServerHandler}
 * To keep the constructor args small and adding new service dependencies can just be done here...
 * Created by navid on 1/23/20
 */
@Component
public class Services {
    @Autowired
    public Continuum continuum;
    @Autowired
    public ContinuumGatewayProperties continuumGatewayProperties;
    @Autowired
    public KinoticRpcProperties kinoticRpcProperties;
    @Autowired
    public EventBusService eventBusService;
    @Autowired
    public EventStreamService eventStreamService;
    @Autowired
    public ExceptionConverter exceptionConverter;
    @Autowired
    public JsonMapper jsonMapper;
    @Autowired
    public SecurityService securityService;
    @Autowired
    public SessionManager sessionManager;
    @Autowired
    public Vertx vertx;
}
