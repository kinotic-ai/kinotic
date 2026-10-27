package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A service acting on a definition's rows across every tenant, as the admin entities repository does.
 */
@AuthzResource(value = "entity_definition", parent = "application", acrossTenants = true)
public interface TestAdminRowsService {

    @AuthzCheck(resourceId = "{entityDefinitionId}")
    CompletableFuture<Long> count(String entityDefinitionId, List<String> tenantSelection);

    @AuthzCheck(permission = "can_delete", resourceId = "{entityDefinitionId}")
    CompletableFuture<Void> deleteByQuery(String entityDefinitionId, String query, List<String> tenantSelection);

}
