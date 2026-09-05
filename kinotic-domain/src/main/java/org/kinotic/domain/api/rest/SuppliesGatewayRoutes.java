package org.kinotic.domain.api.rest;

import io.vertx.ext.web.Router;

/**
 * Contract for any component that contributes REST routes to the API gateway. The gateway collects
 * every {@link SuppliesGatewayRoutes} bean and mounts them, so a module adds routes simply by
 * publishing an implementation — no edits to the gateway are required, and the gateway still boots
 * when a module (and therefore its routes) is absent.
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
