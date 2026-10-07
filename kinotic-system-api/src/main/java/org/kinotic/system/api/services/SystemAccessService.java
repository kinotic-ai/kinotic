package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzUtil;

import java.util.List;

/**
 * Access control of the platform itself, used by the system console: the roles the platform's operators and
 * machines can be granted, and the grants made on the platform, which reach every organization and node on
 * it. Every function acts on the platform, and a subject named must be one of the platform's own operators or
 * machines.
 */
@Publish
@AuthzResource(value = AuthzUtil.PLATFORM_TYPE, resourceId = AuthzUtil.PLATFORM_OBJECT_ID)
public interface SystemAccessService {

    /**
     * Every role a grant on the platform can name: the built-in roles the model defines, each with the
     * permissions it bundles.
     */
    @AuthzCheck(permission = "can_view_access")
    Future<List<RoleDefinition>> findRoles();

    /**
     * The grants made on the platform.
     */
    @AuthzCheck(permission = "can_view_access")
    Future<List<Grant>> findGrants();

    /**
     * Grants a role to a platform operator or machine on the platform. The subject holds every permission the
     * role bundles on the platform and on everything on it: every organization, and every node.
     *
     * @param subject who holds the grant, a user naming an operator's or a machine's identity id
     * @param roleId  the role granted, a built-in role's id
     * @return the grant
     */
    @AuthzCheck(permission = "can_manage_access")
    Future<Grant> grant(Subject subject, String roleId);

    /**
     * Revokes a grant made on the platform.
     *
     * @param grantId the grant
     */
    @AuthzCheck(permission = "can_manage_access")
    Future<Void> revoke(String grantId);

    /**
     * Whether a platform operator or machine holds a permission of the platform, and the grants it holds the
     * permission through.
     *
     * @param subject    the operator or machine
     * @param permission the permission's short name, such as {@code can_manage_workloads}
     */
    @AuthzCheck(permission = "can_view_access")
    Future<AccessExplanation> explain(Subject subject, String permission);

}
