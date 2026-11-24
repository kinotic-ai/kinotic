package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.AuthzNameUtil;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.RolePermissions;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.security.Role;
import org.kinotic.domain.api.model.security.RoleOwner;
import org.kinotic.idl.api.utils.AuthzConstants;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The roles an organization or one of its applications defines for itself: each a row naming it, and the
 * permissions its owner's store bundles for it. An application's own role is homed at the tenant, so it is granted
 * on a tenant or on the application. Checks no participant.
 */
public interface RoleService {

    /** The type every role of an application's own is homed at. */
    String APPLICATION_ROLE_HOME = AuthzConstants.TENANT_TYPE;

    /**
     * A role of an owner's own as the console edits it: homed where its id says, and granted on the types of the
     * owner's tier that hold that home.
     *
     * @param role        the role's row
     * @param owner       the organization or the application the role belongs to
     * @param model       the model the owner's store runs
     * @param permissions what the role bundles in the owner's store
     */
    static RoleDefinition definitionOf(Role role, RoleOwner owner, AuthzModel model, RolePermissions permissions) {
        String home = AuthzNameUtil.roleHome(role.getId());
        Set<String> bundled = new TreeSet<>(permissions.permissions());
        bundled.addAll(permissions.servicePermissions());
        return new RoleDefinition(role.getId(), role.getName(), role.getDescription(), false, home,
                                  model.grantableOn(home, owner.type()),
                                  bundled, permissions.definitionPermissions(), role.isTenantAssignable());
    }

    /**
     * The roles granted in an owner's tier: the built-in roles of the model, then the owner's own in name order,
     * each with the permissions it bundles.
     *
     * @param owner the organization or the application
     * @param model the model the owner's store runs
     */
    Future<List<RoleDefinition>> findRoles(RoleOwner owner, AuthzModel model);

    /**
     * The row of the owner's own role {@code roleId}.
     *
     * @param owner  the organization or the application
     * @param roleId the role's id
     * @return a future emitting the row, or failing with {@link IllegalArgumentException} when the owner has no
     *         role of its own by that id
     */
    Future<Role> requireRole(RoleOwner owner, String roleId);

    /**
     * Creates a role of the owner's own, or edits one: its row is written first and then what it bundles, so a role
     * whose permissions failed to write exists and grants nothing. The role is saved while no grant, other edit or
     * deletion of it is under way, and an edit of a role deleted meanwhile writes nothing.
     *
     * @param owner  the organization or the application
     * @param role   the role's name and description; an id of null creates the role
     * @param home   the type a created role is homed at
     * @param bundle writes what the role bundles to the owner's store, given the saved row
     * @return a future emitting the saved row, or failing with {@link IllegalArgumentException} when the owner has
     *         no role of its own by the id
     */
    Future<Role> saveRole(RoleOwner owner, RoleDefinition role, String home, Function<Role, Future<Void>> bundle);

    /**
     * Deletes a role of the owner's own that no grant holds: what it bundles first and then its row, so a role
     * whose row outlives its permissions grants nothing. A grant of the role made at the same moment either is made
     * first, and refuses the deletion, or finds the role gone.
     *
     * @param owner  the organization or the application
     * @param roleId the role's id
     * @param model  the model the owner's store runs
     * @return a future failing with {@link IllegalArgumentException} when the owner has no role of its own by that
     *         id, and with {@link IllegalStateException} when a grant still holds it
     */
    Future<Void> deleteRole(RoleOwner owner, String roleId, AuthzModel model);

    /**
     * Runs a grant of a role while no edit or deletion of the role is under way, so an edit finds everyone the role
     * is granted to and a deletion every grant of it. A role of the owner's own is read once that is so; a built-in
     * role of the model never changes, and its grant runs at once.
     *
     * @param owner  the organization or the application
     * @param model  the model the owner's store runs
     * @param roleId the role granted
     * @param grant  makes the grant
     * @return what the grant completes with, or a failure with {@link IllegalArgumentException} when the role is
     *         neither built in nor one of the owner's own
     */
    <T> Future<T> whileRoleKept(RoleOwner owner, AuthzModel model, String roleId, Supplier<Future<T>> grant);

    /**
     * Grants a role on a resource of an application's store: a built-in role of the application's model, or one of
     * the application's own, as {@link #whileRoleKept} runs it.
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
