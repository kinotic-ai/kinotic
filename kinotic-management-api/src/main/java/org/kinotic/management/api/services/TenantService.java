package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.idl.api.annotations.McpTool;

/**
 * Manages the {@link Tenant}s of the caller's organization's applications. A tenant's id is derived from its
 * slugified name at creation: lowercase letters, digits, and interior dashes, at most
 * {@link org.kinotic.domain.api.utils.HostLabelUtil#MAX_TENANT_ID_LENGTH} characters so it leads the tenant's
 * site labels, and unique within its application. A tenant's id and name never change after creation, since its
 * users and the rows they write carry its id. Every operation names the application, since two applications may each
 * have a tenant of the same id.
 */
@Publish
@McpTool
public interface TenantService {

    /**
     * Creates a tenant of the application if it does not already exist, deriving its id from the slugified
     * name. The organization id is derived from the authenticated participant.
     *
     * @param applicationId the application the tenant belongs to, one of the caller's organization's
     * @param name          the name of the tenant to create
     * @param description   the description of the tenant to create
     * @return {@link Future} emitting the created tenant, or the application's existing tenant whose id matches the
     *         slugified name
     */
    Future<Tenant> createTenantIfNotExist(String applicationId, String name, String description);

    /**
     * Finds a tenant of the application.
     *
     * @param applicationId the application the tenant belongs to, one of the caller's organization's
     * @param tenantId      the tenant's id
     * @return {@link Future} emitting the tenant, or {@code null} when the application has no tenant of that id
     */
    Future<Tenant> findById(String applicationId, String tenantId);

    /**
     * Counts the tenants of the application.
     *
     * @param applicationId the application whose tenants to count, one of the caller's organization's
     * @return {@link Future} emitting the number of tenants
     */
    Future<Long> countForApplication(String applicationId);

    /**
     * Finds the tenants of the application.
     *
     * @param applicationId the application whose tenants to find, one of the caller's organization's
     * @param pageable      the paging parameters
     * @return {@link Future} emitting a page of the tenants
     */
    Future<Page<Tenant>> findAllForApplication(String applicationId, Pageable pageable);

    /**
     * Finds the tenants of the application whose name or id starts with {@code prefix}, ignoring case; a blank prefix
     * finds them all, as {@link #findAllForApplication} does.
     *
     * @param applicationId the application whose tenants to find, one of the caller's organization's
     * @param prefix        the start of the name or id to match
     * @param pageable      the paging parameters
     * @return {@link Future} emitting a page of the matching tenants
     */
    Future<Page<Tenant>> findForApplicationByPrefix(String applicationId, String prefix, Pageable pageable);

    /**
     * Deletes a tenant of the application, with its email and password sign-in. A tenant created again under the same
     * id starts with no password sign-in of its own. No-op when the application has no tenant of that id.
     *
     * @param applicationId the application the tenant belongs to, one of the caller's organization's
     * @param tenantId      the tenant's id
     * @return {@link Future} completing once the tenant is deleted
     */
    Future<Void> deleteById(String applicationId, String tenantId);

}
