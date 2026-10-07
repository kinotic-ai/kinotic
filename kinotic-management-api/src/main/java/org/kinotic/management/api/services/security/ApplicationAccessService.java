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
 * users and machines can be granted, and the grants made on the application, on one of its tenants, on one of its
 * entity definitions or on a definition within a tenant, which reach the rows below where they are made. A
 * definition within a tenant is named {@code <definition id>@<tenant id>}. Every function names the application,
 * which must belong to the caller's organization, and a subject named must be one of the application's own users
 * or machines.
 */
@Publish
@AuthzResource(value = AuthzUtil.APPLICATION_TYPE, parent = AuthzUtil.ORGANIZATION_TYPE)
public interface ApplicationAccessService {

    /**
     * Every role a grant in the application can name: the built-in roles its model defines, each with the
     * permissions it bundles. A definition's rows have a viewer, an editor and an admin, the same for every
     * definition, a tenant and the application an admin of everything inside.
     *
     * @param applicationId the application
     */
    @AuthzCheck(permission = "can_view_access")
    Future<List<RoleDefinition>> findRoles(String applicationId);

    /**
     * The grants that reach a resource of the application: those made on it, then those made above it, on the
     * tenant and the definition of a definition within a tenant and on the application for everything but itself,
     * each naming the resource it was made on.
     *
     * @param applicationId the application
     * @param resource      the application itself, one of its tenants, one of its entity definitions or a
     *                      definition within a tenant
     */
    @AuthzCheck(permission = "can_view_access")
    Future<List<Grant>> findGrants(String applicationId, Resource resource);

    /**
     * Grants a role to a user or a machine of the application. The subject holds every permission the role
     * bundles on the resource and on everything inside it: a grant on a definition within a tenant reaches those
     * rows alone, one on a tenant every definition's rows in that tenant, one on a definition its rows in every
     * tenant, and one on the application the rows of every definition in every tenant. A definition named must be
     * one the application holds.
     *
     * @param applicationId the application
     * @param subject       who holds the grant, a user naming the identity id of one of the application's users
     *                      or machines
     * @param roleId        the role granted, a built-in role's id
     * @param resource      where the grant is made, the application itself, one of its tenants, one of its entity
     *                      definitions or a definition within a tenant
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
     * @param permission    the permission's model name, such as {@code entity_definition_can_read}
     * @param resource      the application itself, one of its tenants, one of its entity definitions or a
     *                      definition within a tenant
     */
    @AuthzCheck(permission = "can_view_access")
    Future<AccessExplanation> explain(String applicationId, Subject subject, String permission, Resource resource);

}
