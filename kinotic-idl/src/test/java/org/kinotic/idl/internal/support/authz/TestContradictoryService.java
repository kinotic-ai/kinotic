package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;

import java.util.concurrent.CompletableFuture;

/**
 * A service declaring a function zone-only beside a check, which the conversion rejects.
 */
@AuthzResource(value = "organization", objectId = "{@organizationId}")
public interface TestContradictoryService {

    @AuthzCheck(zoneOnly = true, permission = "can_view_members")
    CompletableFuture<Void> findMembers();

}
