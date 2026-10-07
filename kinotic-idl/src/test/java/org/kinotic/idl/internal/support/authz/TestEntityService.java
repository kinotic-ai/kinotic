package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;

import java.util.concurrent.CompletableFuture;

/**
 * A service whose resource type is an argument of the call.
 */
@AuthzResource("entity")
public interface TestEntityService {

    @AuthzCheck(resource = "{entityDefinitionId}", resourceId = "{id}")
    CompletableFuture<String> findById(String entityDefinitionId, String id);

}
