package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.services.ApplicationScopedCrudService;
import org.kinotic.idl.api.annotations.McpTool;

/**
 * Manages the {@link Tenant}s of the caller's organization's applications. A tenant's id is derived from its
 * slugified name at creation: lowercase letters, digits, and interior dashes, short enough to lead the
 * tenant's site labels.
 */
@Publish
@McpTool
public interface TenantService extends ApplicationScopedCrudService<Tenant, String> {

    /**
     * Creates a tenant of the application if it does not already exist, deriving its id from the slugified
     * name. The organization id is derived from the authenticated participant.
     *
     * @param applicationId the application the tenant belongs to, one of the caller's organization's
     * @param name          the name of the tenant to create
     * @param description   the description of the tenant to create
     * @return {@link Future} emitting the created tenant, or the application's existing tenant whose id
     *         matches the slugified name
     */
    Future<Tenant> createTenantIfNotExist(String applicationId, String name, String description);

}
