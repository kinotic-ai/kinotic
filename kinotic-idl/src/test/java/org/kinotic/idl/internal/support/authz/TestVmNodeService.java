package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;

import java.util.concurrent.CompletableFuture;

/**
 * A resource service under the platform itself, with a check on the platform and one reaching into an argument.
 */
@AuthzResource(value = "vm_node", parent = "platform")
public interface TestVmNodeService {

    @AuthzCheck(resource = "platform", permission = "can_register_node")
    CompletableFuture<TestProject> register(TestProject registration);

    @AuthzCheck(permission = "can_heartbeat", objectId = "{registration.id}")
    CompletableFuture<Void> heartbeat(TestProject registration);

    CompletableFuture<Void> deleteByVmNodeId(String vmNodeId);

}
