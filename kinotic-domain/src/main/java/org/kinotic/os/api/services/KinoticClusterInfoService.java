package org.kinotic.os.api.services;

// import org.kinotic.rpc.api.annotations.Publish;
// import org.kinotic.rpc.api.annotations.Version;
import org.kinotic.os.api.model.cluster.KinoticClusterInfo;

import java.util.concurrent.CompletableFuture;

/**
 * Provides information about the ignite Kinotic cluster.
 */
// @Publish
// @Version("1.0.0")
public interface KinoticClusterInfoService {
    
    /**
     * Returns the information about the ignite structures cluster.
     * 
     * @return the information about the ignite structures cluster
     */
    CompletableFuture<KinoticClusterInfo> getClusterInfo();

}
