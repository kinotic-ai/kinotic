package org.mindignited.structures.api.services.cluster;

// import org.mindignited.continuum.api.annotations.Publish;
// import org.mindignited.continuum.api.annotations.Version;
import org.mindignited.structures.api.domain.cluster.ClusterInfo;

import reactor.core.publisher.Mono;

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
    Mono<ClusterInfo> getClusterInfo();

}
