package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.annotations.AuthzRole;

import java.util.concurrent.CompletableFuture;

/**
 * A resource service declaring a role at the level of a built-in role of its type.
 */
@AuthzResource(value = "widget", roles = @AuthzRole(id = "widget.viewer", permissions = "can_read"))
public interface TestBuiltInRoleService {

    CompletableFuture<Void> find(String id);

}
