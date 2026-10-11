package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.annotations.AuthzUnchecked;

import java.util.concurrent.CompletableFuture;

/**
 * A service declaring a function unchecked beside a check, which the conversion rejects.
 */
@AuthzResource(value = "organization", resourceId = "{@organizationId}")
public interface TestContradictoryService {

    @AuthzUnchecked
    @AuthzCheck(permission = "can_view_members")
    CompletableFuture<Void> findMembers();

}
