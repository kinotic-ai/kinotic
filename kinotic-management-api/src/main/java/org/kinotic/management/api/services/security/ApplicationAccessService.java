package org.kinotic.management.api.services.security;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzUtil;

import java.util.List;

/**
 * The access control of one application's users, in the application's own authorization store: the roles its
 * users and machines can be granted, and the grants made on the application or on one of its tenants, which
 * reach every row of the application's entity definitions inside. Every function names the application, which
 * must belong to the caller's organization, and a subject named must be one of the application's own users or
 * machines.
 */
@Publish
@AuthzResource(value = AuthzUtil.APPLICATION_TYPE, parent = AuthzUtil.ORGANIZATION_TYPE)
public interface ApplicationAccessService {

    /**
     * Every role a grant in the application can name: the built-in roles its model defines, each with the
     * permissions it bundles. An entity definition's rows have a viewer, an editor and an admin, a tenant and
     * the application an admin of everything inside.
     *
     * @param applicationId the application
     */
    @AuthzCheck(permission = "can_view_access")
    Future<List<RoleDefinition>> findRoles(String applicationId);

    /**
     * The grants that reach a resource of the application: those made on it, then those made on the
     * application when the resource is one of its tenants, each naming the resource it was made on.
     *
     * @param applicationId the application
     * @param resource      the application itself, or one of its tenants
     */
    @AuthzCheck(permission = "can_view_access")
    Future<List<Grant>> findGrants(String applicationId, Resource resource);

    /**
     * Grants a role to a user or a machine of the application on the application or on one of its tenants. The
     * subject holds every permission the role bundles on the resource and on everything inside it: a grant on
     * a tenant reaches the rows of that tenant, a grant on the application the rows of every tenant.
     *
     * @param applicationId the application
     * @param subject       who holds the grant, a user naming the identity id of one of the application's users
     *                      or machines
     * @param roleId        the role granted, a built-in role's id
     * @param resource      where the grant is made, the application itself or one of its tenants
     * @return the grant
     */
    @AuthzCheck(permission = "can_manage_access")
    Future<Grant> grant(String applicationId, Subject subject, String roleId, Resource resource);

    /**
     * Revokes a grant where it was made.
     *
     * @param applicationId the application
     * @param resource      where the grant was made
     * @param grantId       the grant
     */
    @AuthzCheck(permission = "can_manage_access")
    Future<Void> revoke(String applicationId, Resource resource, String grantId);

    /**
     * Whether a user or a machine of the application holds a permission on a resource, and the grants it holds
     * the permission through.
     *
     * @param applicationId the application
     * @param subject       the user or machine
     * @param permission    the permission's model name, such as {@code invoice_can_read}
     * @param resource      the application itself or one of its tenants
     */
    @AuthzCheck(permission = "can_view_access")
    Future<AccessExplanation> explain(String applicationId, Subject subject, String permission, Resource resource);

}
