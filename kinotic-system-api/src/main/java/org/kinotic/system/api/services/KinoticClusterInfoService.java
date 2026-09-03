package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.system.api.model.cluster.KinoticClusterInfo;

/**
 * Provides information about the ignite Kinotic cluster.
 */
@Publish
public interface KinoticClusterInfoService {
    
    /**
     * Returns the information about the ignite structures cluster.
     * 
     * @return the information about the ignite structures cluster
     */
    Future<KinoticClusterInfo> getClusterInfo();

}
