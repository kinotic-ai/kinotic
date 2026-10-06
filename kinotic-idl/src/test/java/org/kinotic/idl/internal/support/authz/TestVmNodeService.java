package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.annotations.AuthzRole;

import java.util.concurrent.CompletableFuture;

/**
 * A resource service under the platform itself, with a check on the platform, one reaching into an argument,
 * and a role of its own.
 */
@AuthzResource(value = "vm_node", parent = "platform",
               roles = @AuthzRole(id = "vm_node.registrar", permissions = "can_register_node"))
public interface TestVmNodeService {

    @AuthzCheck(resource = "platform", permission = "can_register_node")
    CompletableFuture<TestProject> register(TestProject registration);

    @AuthzCheck(permission = "can_heartbeat", resourceId = "{registration.id}")
    CompletableFuture<Void> heartbeat(TestProject registration);

    CompletableFuture<Void> deleteByVmNodeId(String vmNodeId);

}
