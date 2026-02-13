package org.kinotic.core.api.services;

// import org.kinotic.rpc.api.annotations.Publish;
// import org.kinotic.rpc.api.annotations.Version;
import org.kinotic.core.api.domain.cluster.ClusterInfo;

import java.util.concurrent.CompletableFuture;

/**
 * Provides information about the ignite structures cluster.
 */
// @Publish
// @Version("1.0.0")
public interface ClusterInfoService {
    
    /**
     * Returns the information about the ignite structures cluster.
     * 
     * @return the information about the ignite structures cluster
     */
    CompletableFuture<ClusterInfo> getClusterInfo();

}
