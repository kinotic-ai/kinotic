package org.kinotic.idl.internal.support.authz;

import org.kinotic.idl.api.annotations.AuthzResource;

import java.util.concurrent.CompletableFuture;

/**
 * A service of a type other than the definition's declared acting across tenants, which the conversion refuses.
 */
@AuthzResource(value = "widget", acrossTenants = true)
public interface TestAcrossTenantsWidgetService {

    CompletableFuture<String> findById(String id);

}
