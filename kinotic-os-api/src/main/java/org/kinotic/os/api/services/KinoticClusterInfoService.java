package org.kinotic.os.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.domain.api.model.cluster.KinoticClusterInfo;
import org.kinotic.domain.api.utils.DomainUtil;

/**
 * Provides information about the ignite Kinotic cluster.
 */
@Publish
@Zone(DomainUtil.SYSTEM_ZONE)
public interface KinoticClusterInfoService {
    
    /**
     * Returns the information about the ignite structures cluster.
     * 
     * @return the information about the ignite structures cluster
     */
    Future<KinoticClusterInfo> getClusterInfo();

}
