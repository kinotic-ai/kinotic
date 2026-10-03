package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;

import java.util.concurrent.CompletableFuture;

/**
 * A resource service whose declared object id names a parameter the function does not have.
 */
@AuthzResource("widget")
public interface TestMisreferencingService {

    @AuthzCheck(permission = "can_view", objectId = "{missing}")
    CompletableFuture<Void> find(String id);

}
