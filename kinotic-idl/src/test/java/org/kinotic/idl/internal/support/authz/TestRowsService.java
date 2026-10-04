package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;

import java.util.concurrent.CompletableFuture;

/**
 * A service acting on the rows of whichever entity definition each request names, checked on the caller's tenant.
 */
@AuthzResource(value = "{entityDefinitionId}", parent = "tenant")
public interface TestRowsService {

    @AuthzCheck(resource = "tenant", permission = "can_read")
    CompletableFuture<String> findById(String entityDefinitionId, String id);

    @AuthzCheck(resource = "tenant", permission = "can_search")
    CompletableFuture<Long> count(String entityDefinitionId);

}
