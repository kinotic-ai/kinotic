package org.kinotic.boot.api;

/**
 * Contains information about this Kinotic process
 *
 * Created by navid on 9/24/19
 */
public interface Kinotic {

    /**
     * Returns information about this Kinotic server.
     * @return the {@link ServerInfo} object
     */
    ServerInfo serverInfo();

}
