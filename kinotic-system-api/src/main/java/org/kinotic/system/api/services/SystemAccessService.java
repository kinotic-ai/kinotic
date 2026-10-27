package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzConstants;

import java.util.List;

/**
 * Access control of the system itself, used by the system console: the roles the system users and
 * machines can be granted, and the grants made on the system, which reach every organization and node on
 * it. Every function acts on the system, and a subject named must be one of the system users or
 * machines.
 */
@Publish
@AuthzResource(value = AuthzConstants.SYSTEM_TYPE, resourceId = AuthzConstants.SYSTEM_OBJECT_ID)
public interface SystemAccessService {

    /**
     * Every role a grant on the system can name: the built-in roles the model defines, each with the
     * permissions it bundles.
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_ACCESS)
    Future<List<RoleDefinition>> findRoles();

    /**
     * The grants made on the system.
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_ACCESS)
    Future<List<Grant>> findGrants();

    /**
     * Grants a role to a system user or machine on the system. The subject holds every permission the
     * role bundles on the system and on everything on it: every organization, and every node.
     *
     * @param subject who holds the grant, a user naming a system user's or a machine's identity id
     * @param roleId  the role granted, a built-in role's id
     * @return the grant
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_ACCESS)
    Future<Grant> grant(Subject subject, String roleId);

    /**
     * Revokes a grant made on the system. The system keeps at least one administrator who can sign in, so the
     * last grant of the administrator role held by an enabled system user is refused.
     *
     * @param grantId the grant
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_ACCESS)
    Future<Void> revoke(String grantId);

    /**
     * Whether a system user or machine holds a permission of the system, and the grants it holds the
     * permission through.
     *
     * @param subject    the system user or machine
     * @param permission the permission's short name, such as {@code can_manage_workloads}
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_ACCESS)
    Future<AccessExplanation> explain(Subject subject, String permission);

}
