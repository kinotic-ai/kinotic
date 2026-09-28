package org.kinotic.domain.api.rest;

import io.vertx.ext.web.Router;

/**
 * Contract for a handler that contributes REST routes to the API gateway. The gateway collects every
 * {@link SuppliesGatewayRoutes} bean and mounts them. A handler is not a scanned component: a server serves
 * it by importing it on its application class, {@code @Import(OrganizationLoginHandler.class)}, so the routes
 * a server serves are read from that one class.
 */
public interface SuppliesGatewayRoutes {

    /**
     * Registers this supplier's routes on the given router. Called once during gateway startup with
     * the router that already has the gateway's global handlers (CORS, body, session) installed.
     *
     * @param router the gateway router to mount routes on
     */
    void mountRoutes(Router router);
}
