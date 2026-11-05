package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;

import java.util.concurrent.CompletableFuture;

/**
 * A resource service declaring a permission beside the resolver deciding it, which the conversion rejects.
 */
@AuthzResource("report")
public interface TestResolverBesidePermissionService {

    @AuthzCheck(permission = "can_read", permissionResolver = TestStatementPermissionResolver.NAME, resourceId = "{reportId}")
    CompletableFuture<Void> runQuery(String reportId, String queryName);

}
