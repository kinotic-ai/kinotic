package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;

import java.util.concurrent.CompletableFuture;

/**
 * A service whose check names its resource's type by a template, which the conversion refuses.
 */
@AuthzResource("entity")
public interface TestEntityService {

    @AuthzCheck(resource = "{entityDefinitionId}", resourceId = "{id}")
    CompletableFuture<String> findById(String entityDefinitionId, String id);

}
