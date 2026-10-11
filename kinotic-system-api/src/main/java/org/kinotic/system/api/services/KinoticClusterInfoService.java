package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.system.api.model.cluster.KinoticClusterInfo;

/**
 * Provides information about the ignite Kinotic cluster.
 */
@Publish
@AuthzResource(value = AuthzUtil.PLATFORM_TYPE, resourceId = AuthzUtil.PLATFORM_OBJECT_ID)
public interface KinoticClusterInfoService {
    
    /**
     * Returns the information about the ignite structures cluster.
     * 
     * @return the information about the ignite structures cluster
     */
    @AuthzCheck(permission = AuthzUtil.CAN_VIEW_CLUSTER)
    Future<KinoticClusterInfo> getClusterInfo();

}
