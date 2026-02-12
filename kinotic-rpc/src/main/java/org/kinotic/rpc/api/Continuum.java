package org.kinotic.rpc.api;

import org.kinotic.rpc.api.annotations.EnableKinoticRpc;

/**
 * Contains information about this Continuum process
 *
 * Created by navid on 9/24/19
 */
public interface Continuum {

    /**
     * Returns information about this Continuum server.
     * @return the {@link ServerInfo} object
     */
    ServerInfo serverInfo();

    /**
     * Returns the name of the application.
     * This comes from the class annotated with {@link EnableKinoticRpc}
     * @return the name of the application
     */
    String applicationName();

    /**
     * Returns the version of the application
     * This comes from the class annotated with {@link EnableKinoticRpc}
     * @return the version of the application
     */
    String applicationVersion();

}
