package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;

import java.util.concurrent.CompletableFuture;

/**
 * A resource service whose function lacks a parameter its permission resolver reads, which the conversion rejects.
 */
@AuthzResource("report")
public interface TestResolverMissingParameterService {

    @AuthzCheck(permissionResolver = TestStatementPermissionResolver.NAME, resourceId = "{reportId}")
    CompletableFuture<Void> runQuery(String reportId);

}
