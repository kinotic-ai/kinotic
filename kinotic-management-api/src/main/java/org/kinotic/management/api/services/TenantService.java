package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.services.ApplicationScopedCrudService;
import org.kinotic.idl.api.annotations.McpTool;

/**
 * Manages the {@link Tenant}s of the caller's organization's applications. A tenant's id is derived from its
 * slugified name at creation: lowercase letters, digits, and interior dashes, at most
 * {@link org.kinotic.domain.api.utils.HostLabelUtil#MAX_TENANT_ID_LENGTH} characters so it leads the tenant's
 * site labels, and unique within the organization.
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
     *         matches the slugified name; fails when that id is a tenant of another of the organization's
     *         applications, since a tenant id is unique within the organization
     */
    Future<Tenant> createTenantIfNotExist(String applicationId, String name, String description);

    /**
     * Finds the tenants of the application whose name or id starts with {@code prefix}, ignoring case; a blank prefix
     * finds them all, as {@code findAllForApplication} does.
     *
     * @param applicationId the application whose tenants to find, one of the caller's organization's
     * @param prefix        the start of the name or id to match
     * @param pageable      the paging parameters
     * @return {@link Future} emitting a page of the matching tenants
     */
    Future<Page<Tenant>> findForApplicationByPrefix(String applicationId, String prefix, Pageable pageable);

}
