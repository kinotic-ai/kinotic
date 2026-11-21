package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.management.api.model.Capabilities;

/**
 * Tells a UI which optional features this Kinotic server offers.
 */
@Publish
public interface CapabilityService {

    /**
     * Finds which optional features this server offers.
     *
     * @return a future emitting the server's capabilities
     */
    Future<Capabilities> findCapabilities();

}
