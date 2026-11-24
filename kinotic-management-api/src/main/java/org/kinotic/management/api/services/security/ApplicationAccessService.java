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
import org.kinotic.idl.api.utils.AuthzConstants;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The access control of one application's users, in the application's own authorization store: the roles its
 * users and machines can be granted, the application's own roles among them, and the grants made on the
 * application, on one of its tenants, on one of its entity definitions or on a definition within a tenant, which
 * reach the rows below where they are made. A definition within a tenant is named
 * {@code <definition id>@<tenant id>}. Every function names the application, which must belong to the caller's
 * organization, and a subject named must be one of the application's own users or machines, or one of its tenants,
 * standing for every user of that tenant.
 * <p>
 * A role of the application's own says what its holders may do with each entity and each service of the
 * application: {@code Accountant} reads and edits the rows of {@code Invoice} and reads those of
 * {@code InventoryItem}. Reading, editing and deleting the rows of a definition are each held on their own. An
 * entity no role held by a user names is reached by the application's microservices alone.
 */
@Publish
@AuthzResource(value = AuthzConstants.APPLICATION_TYPE, parent = AuthzConstants.ORGANIZATION_TYPE)
public interface ApplicationAccessService {

    /**
     * The permissions a role of the application's own may bundle, by resource type: the model names of the
     * permissions of a tenant and of everything in it, such as {@code entity_definition_can_read} under
     * {@code entity_definition} and the permissions of each type the application's services declare.
     *
     * @param applicationId the application
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_ACCESS)
    Future<Map<String, Set<String>>> findPermissions(String applicationId);

    /**
     * Every role a grant in the application can name: the built-in roles its model defines, then the
     * application's own roles, each with the permissions it bundles. A definition's rows have a viewer, an editor
     * and an admin, the same for every definition, a tenant and the application an admin of everything inside.
     *
     * @param applicationId the application
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_ACCESS)
    Future<List<RoleDefinition>> findRoles(String applicationId);

    /**
     * Creates or updates a role of the application's own: its name, its description and the permissions it
     * bundles, which take effect on every grant of the role at once. The role is homed at the tenant, so it is
     * granted on a tenant or on the application. Its {@code permissions} are held on everything such a grant
     * reaches, and its {@code definitionPermissions} on the entity definitions they name alone. While an edit
     * changes the definitions a permission is bundled for, the role's holders are refused that permission. Fails
     * for a built-in role, for a permission a tenant's role cannot bundle, for a definition the application does
     * not hold, and for an id that names no role of the application.
     *
     * @param applicationId the application
     * @param role          the role, with a null id for a new one
     * @return the role as saved
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_ACCESS, consistent = true)
    Future<RoleDefinition> saveRole(String applicationId, RoleDefinition role);

    /**
     * Deletes a role of the application's own. Fails for a built-in role and while a grant holds the role, since
     * a grant is revoked where it was made.
     *
     * @param applicationId the application
     * @param roleId        the role
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_ACCESS, consistent = true)
    Future<Void> deleteRole(String applicationId, String roleId);

    /**
     * The grants that reach a resource of the application: those made on it, then those made above it, on the
     * tenant and the definition of a definition within a tenant and on the application for everything but itself,
     * each naming the resource it was made on.
     *
     * @param applicationId the application
     * @param resource      the application itself, one of its tenants, one of its entity definitions or a
     *                      definition within a tenant
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_ACCESS)
    Future<List<Grant>> findGrants(String applicationId, Resource resource);

    /**
     * Grants a role to a user or a machine of the application, or with one grant to every user of one of its
     * tenants or to every end user of the application, those there are now and those created later. The subject
     * holds every permission the role bundles on the resource and on everything inside it: a grant on a definition
     * within a tenant reaches those rows alone, one on a tenant every definition's rows in that tenant, one on a
     * definition its rows in every tenant, and one on the application the rows of every definition in every tenant.
     * An application's user is asked about within its own tenant alone, so a role granted to every end user on the
     * application is the role each user starts with in its own tenant. A definition named must be one the
     * application holds, and a tenant named one the application has where its administrators manage its tenants.
     *
     * @param applicationId the application
     * @param subject       who holds the grant, a user naming the identity id of one of the application's users
     *                      or machines, a tenant naming the id of one of its tenants, or an application naming
     *                      the application itself for every end user it has
     * @param roleId        the role granted, a built-in role's id or the id of one of the application's own
     * @param resource      where the grant is made, the application itself, one of its tenants, one of its entity
     *                      definitions or a definition within a tenant
     * @return the grant
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_ACCESS, consistent = true)
    Future<Grant> grant(String applicationId, Subject subject, String roleId, Resource resource);

    /**
     * Revokes a grant where it was made.
     *
     * @param applicationId the application
     * @param resource      where the grant was made
     * @param grantId       the grant
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_ACCESS, consistent = true)
    Future<Void> revoke(String applicationId, Resource resource, String grantId);

    /**
     * Whether a user or a machine of the application, or every user of one of its tenants, holds a permission on
     * a resource, and the grants it holds the permission through.
     *
     * @param applicationId the application
     * @param subject       the user, the machine or the tenant
     * @param permission    the permission's model name, such as {@code entity_definition_can_read}
     * @param resource      the application itself, one of its tenants, one of its entity definitions or a
     *                      definition within a tenant
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_ACCESS)
    Future<AccessExplanation> explain(String applicationId, Subject subject, String permission, Resource resource);

}
