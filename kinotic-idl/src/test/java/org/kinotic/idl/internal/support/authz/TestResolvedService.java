package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;

import java.util.concurrent.CompletableFuture;

/**
 * A resource service whose query is checked for the permission a resolver decides per request.
 */
@AuthzResource("report")
public interface TestResolvedService {

    @AuthzCheck(permissionResolver = TestStatementPermissionResolver.NAME, resourceId = "{reportId}")
    CompletableFuture<Void> runQuery(String reportId, String queryName);

}
