package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.annotations.AuthzRole;

import java.util.concurrent.CompletableFuture;

/**
 * A resource service declaring a role not named after its type.
 */
@AuthzResource(value = "widget", roles = @AuthzRole(id = "gadget.keeper", permissions = "can_view"))
public interface TestMisnamedRoleService {

    CompletableFuture<Void> find(String id);

}
