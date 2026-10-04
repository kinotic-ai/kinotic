package org.kinotic.domain.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzUtil;

/**
 * The tenant a user of an application belongs to, for the application's own pages: what it is called, and the
 * settings its administrator keeps. Every function acts on the caller's tenant, so only an application
 * participant that belongs to one may call, and what the caller may do is checked on that tenant in the
 * application's store.
 */
@Publish
@Version("1.0.0")
@Zone(DomainUtil.APP_API_ZONE)
@AuthzResource(value = AuthzUtil.TENANT_TYPE, parent = AuthzUtil.APPLICATION_TYPE, objectId = "{@tenantId}")
public interface TenantService {

    /**
     * The caller's tenant.
     *
     * @return the tenant; fails when the application has no record of it
     */
    Future<Tenant> getTenant();

    /**
     * Renames the caller's tenant; its id stays what it was.
     *
     * @param name the new name, not blank
     * @return the tenant as saved
     */
    @AuthzCheck(permission = AuthzUtil.CAN_EDIT)
    Future<Tenant> rename(String name);

}
