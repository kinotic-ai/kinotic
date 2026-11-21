package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.RolePermissions;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.security.Role;
import org.kinotic.idl.api.utils.AuthzConstants;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The roles of an application's own, which its developers define for its users, and the grants made in the
 * application's store. An application's own role is homed at the tenant, so it is granted on a tenant or on the
 * application. Checks no participant.
 */
public interface ApplicationRoleService {

    /** The type every role of an application's own is homed at. */
    String ROLE_HOME = AuthzConstants.TENANT_TYPE;

    /**
     * A role of an application's own as the console edits it.
     *
     * @param role        the role's row
     * @param model       the application's model
     * @param permissions what the role bundles in the application's store
     */
    static RoleDefinition definitionOf(Role role, AuthzModel model, RolePermissions permissions) {
        Set<String> bundled = new TreeSet<>(permissions.permissions());
        bundled.addAll(permissions.servicePermissions());
        return new RoleDefinition(role.getId(), role.getName(), role.getDescription(), false, ROLE_HOME,
                                  model.grantableOn(ROLE_HOME, AuthzConstants.APPLICATION_TYPE),
                                  bundled, permissions.definitionPermissions(), role.isTenantAssignable());
    }

    /**
     * The application's own roles, in name order, each with the permissions it bundles.
     *
     * @param application the application
     */
    Future<List<RoleDefinition>> findRoles(Application application);

    /**
     * The row of the application's own role {@code roleId}.
     *
     * @param application the application
     * @param roleId      the role's id
     * @return a future emitting the row, or failing with {@link IllegalArgumentException} when the application has
     *         no role of its own by that id
     */
    Future<Role> requireRole(Application application, String roleId);

    /**
     * Grants a role on a resource of the application's store: a built-in role of the application's model, or one of
     * the application's own, which is granted while no change or deletion of it is under way, so a deletion finds
     * every grant of it.
     *
     * @param application the application
     * @param subject     who the role is granted to
     * @param roleId      the role
     * @param resource    the resource the grant is made on, named as the application's organization knows it
     * @return a future emitting the grant, or failing with {@link IllegalArgumentException} when the role cannot be
     *         granted on the resource's type or the application has no such role
     */
    Future<Grant> grant(Application application, Subject subject, String roleId, Resource resource);

}
