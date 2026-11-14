package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;

import java.util.concurrent.CompletableFuture;

/**
 * A resource service naming a permission resolver that no resolver has the name of, which the conversion rejects.
 */
@AuthzResource("report")
public interface TestUnknownResolverService {

    @AuthzCheck(permissionResolver = "nobody", resourceId = "{reportId}")
    CompletableFuture<Void> runQuery(String reportId, String queryName);

}
