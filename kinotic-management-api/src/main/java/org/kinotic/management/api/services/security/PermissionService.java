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
import org.kinotic.idl.api.annotations.AuthzUnchecked;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.Group;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Access control for the caller's organization, used by the console: the roles a member can be granted, the
 * groups a grant can be made to, and the grants made on the organization and on everything inside it. Every
 * function derives the organization from the authenticated participant, so only organization members may call,
 * and a role, a group, a user or a resource named must belong to that organization.
 */
@Publish
@AuthzResource(value = AuthzUtil.ORGANIZATION_TYPE, resourceId = "{@organizationId}")
public interface PermissionService {

    /**
     * The permissions a role may bundle, by resource type: the model names of every permission the platform's
     * services declare, such as {@code project_can_edit} under {@code project}.
     */
    @AuthzCheck(permission = "can_view_access")
    Future<Map<String, Set<String>>> findPermissions();

    /**
     * Every role a grant can name: the built-in roles the model defines, then the organization's custom roles,
     * each with the permissions it bundles.
     */
    @AuthzCheck(permission = "can_view_access")
    Future<List<RoleDefinition>> findRoles();

    /**
     * Creates or updates a custom role: its name, its description and the permissions it bundles, which take
     * effect on every grant of the role at once. Fails for a built-in role, for a permission the model does not
     * have, and for an id that names no role of the organization.
     *
     * @param role the role, with a null id for a new one
     * @return the role as saved
     */
    @AuthzCheck(permission = "can_manage_access")
    Future<RoleDefinition> saveRole(RoleDefinition role);

    /**
     * Deletes a custom role. Fails for a built-in role and while a grant holds the role, since a grant is revoked
     * where it was made.
     *
     * @param roleId the role
     */
    @AuthzCheck(permission = "can_manage_access")
    Future<Void> deleteRole(String roleId);

    /**
     * Lists the organization's groups.
     *
     * @param pageable the page to return
     */
    @AuthzCheck(permission = "can_view_access")
    Future<Page<Group>> findGroups(Pageable pageable);

    /**
     * Creates or updates a group's name and description. A new group starts with no members.
     *
     * @param group the group, with a null id for a new one
     * @return the group as saved
     */
    @AuthzCheck(permission = "can_manage_access")
    Future<Group> saveGroup(Group group);

    /**
     * Deletes a group with its membership. Fails while a grant holds the group, since a grant is revoked where it
     * was made.
     *
     * @param groupId the group
     */
    @AuthzCheck(permission = "can_manage_access")
    Future<Void> deleteGroup(String groupId);

    /**
     * The members of a group, whole: a group of an organization's members is read at once.
     *
     * @param groupId the group
     */
    @AuthzCheck(permission = "can_view_access")
    Future<List<UserParticipantIdentity>> findGroupMembers(String groupId);

    /**
     * Adds a member of the organization to a group. A user already in the group is left as it is.
     *
     * @param groupId the group
     * @param userId  the member's identity id
     */
    @AuthzCheck(permission = "can_manage_access")
    Future<Void> addGroupMember(String groupId, String userId);

    /**
     * Removes a user from a group. A user not in the group is left as it is.
     *
     * @param groupId the group
     * @param userId  the user's identity id
     */
    @AuthzCheck(permission = "can_manage_access")
    Future<Void> removeGroupMember(String groupId, String userId);

    /**
     * Grants a role to a user or a group on the organization, or on an application, a project or an entity
     * definition inside it. The subject holds every permission the role bundles on the resource and on
     * everything inside it.
     *
     * @param subject  who holds the grant
     * @param roleId   the role granted, a built-in role's id or a custom role's
     * @param resource where the grant is made
     * @return the grant
     */
    @AuthzCheck(permission = "can_manage_access")
    Future<Grant> grant(Subject subject, String roleId, Resource resource);

    /**
     * Revokes a grant where it was made.
     *
     * @param resource where the grant was made
     * @param grantId  the grant
     */
    @AuthzCheck(permission = "can_manage_access")
    Future<Void> revoke(Resource resource, String grantId);

    /**
     * The grants that reach a resource: those made on it, then those made on each of its ancestors up to the
     * organization, each naming the resource it was made on.
     *
     * @param resource the resource
     */
    @AuthzCheck(permission = "can_view_access")
    Future<List<Grant>> findGrants(Resource resource);

    /**
     * The resources of a type the caller holds a permission on, through any grant that reaches them.
     *
     * @param type       the resource type
     * @param permission the permission's short name, such as {@code can_view}
     * @return the ids of the resources
     */
    @AuthzUnchecked
    Future<List<String>> listAccessible(String type, String permission);

    /**
     * Whether a subject holds a permission on a resource, and the grants it holds the permission through.
     *
     * @param subject    the user or group
     * @param permission the permission's short name, such as {@code can_edit}
     * @param resource   the resource
     */
    @AuthzCheck(permission = "can_view_access")
    Future<AccessExplanation> explain(Subject subject, String permission, Resource resource);
}
