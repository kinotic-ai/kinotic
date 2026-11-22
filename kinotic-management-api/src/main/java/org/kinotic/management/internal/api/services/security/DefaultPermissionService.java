package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.AuthzNameUtil;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.RolePermissions;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectType;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.ConnectionRevocationService;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.Group;
import org.kinotic.domain.api.model.security.RoleOwner;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.repositories.GroupRepository;
import org.kinotic.domain.api.services.security.AuthzModelService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.RoleService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.services.security.PermissionService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.kinotic.authz.api.services.AuthzStoreService.SYSTEM;

@Component
@RequiredArgsConstructor
public class DefaultPermissionService implements PermissionService {

    private final SecurityContext securityContext;
    private final AuthzModelService models;
    private final RelationshipService relationships;
    private final RoleService roleService;
    private final GroupRepository groups;
    private final ParticipantIdentityService identities;
    private final ConnectionRevocationService connectionRevocationService;
    private final CallerPermissions callerPermissions;

    @Override
    public Future<Map<String, Set<String>>> findPermissions() {
        requireOrgParticipant();
        return model().map(model -> model.catalog(AuthzConstants.ORGANIZATION_TYPE));
    }

    @Override
    public Future<List<RoleDefinition>> findRoles() {
        RoleOwner owner = RoleOwner.organization(requireOrgParticipant().getOrganizationId());
        return model().compose(model -> roleService.findRoles(owner, model));
    }

    @Override
    public Future<RoleDefinition> saveRole(RoleDefinition role) {
        Validate.notNull(role, "role cannot be null");
        Validate.notBlank(role.name(), "role name cannot be blank");
        Validate.notNull(role.permissions(), "role permissions cannot be null");
        String organizationId = requireOrgParticipant().getOrganizationId();
        return model().compose(model -> {
            if (role.id() != null && model.roles().containsKey(role.id())) {
                throw new IllegalArgumentException("Role " + role.id() + " is built in and defined by the model");
            }
            String home = role.id() == null ? role.resourceType() : AuthzNameUtil.roleHome(role.id());
            if (home == null || !model.tier(AuthzConstants.ORGANIZATION_TYPE).contains(home)) {
                throw new IllegalArgumentException("A role of the organization is homed at one of " + model.tier(AuthzConstants.ORGANIZATION_TYPE)
                                                           + ", not " + home);
            }
            if (role.resourceType() != null && !role.resourceType().equals(home)) {
                throw new IllegalArgumentException("Role " + role.id() + " is homed at " + home + ", which cannot change");
            }
            Set<String> catalog = new HashSet<>();
            model.catalog(home).values().forEach(catalog::addAll);
            for (String permission : role.permissions()) {
                if (!catalog.contains(permission)) {
                    throw new IllegalArgumentException("No permission a role homed at " + home + " can bundle is named '" + permission + "'");
                }
            }
            RoleOwner owner = RoleOwner.organization(organizationId);
            RolePermissions permissions = new RolePermissions(role.permissions());
            // every machine the role is granted to is among the holders read here, under the lock a grant of the role
            // takes, or is granted the role as this edit leaves it
            return roleService.saveRole(owner, role, home, saved -> relationships.findRolePermissions(SYSTEM, saved.getId())
                                      // read before the permissions are written, so an edit whose read failed is
                                      // made again as the same edit
                                      .compose(before -> role.permissions().containsAll(before.permissions())
                                              ? Future.succeededFuture(List.<String>of())
                                              : holders(saved.getId(), model.grantableOn(home, AuthzConstants.ORGANIZATION_TYPE)).compose(this::machines))
                                      .compose(machines -> relationships.ensureRole(SYSTEM, saved.getId(), permissions)
                                                                        .compose(v -> reconnected(machines))))
                              .map(saved -> RoleService.definitionOf(saved, owner, model, permissions));
        });
    }

    @Override
    public Future<Void> deleteRole(String roleId) {
        Validate.notBlank(roleId, "roleId cannot be blank");
        RoleOwner owner = RoleOwner.organization(requireOrgParticipant().getOrganizationId());
        return model().compose(model -> roleService.deleteRole(owner, roleId, model));
    }

    @Override
    public Future<Page<Group>> findGroups(Pageable pageable) {
        Validate.notNull(pageable, "pageable cannot be null");
        return groups.findAll(requireOrgParticipant().getOrganizationId(), pageable);
    }

    @Override
    public Future<Group> saveGroup(Group group) {
        Validate.notNull(group, "group cannot be null");
        Validate.notBlank(group.getName(), "group name cannot be blank");
        String organizationId = requireOrgParticipant().getOrganizationId();
        Future<Group> row = group.getId() == null
                ? Future.succeededFuture(new Group().setId(UUID.randomUUID().toString()).setOrganizationId(organizationId).setCreated(new Date()))
                : requireGroup(group.getId(), organizationId);
        return row.compose(existing -> groups.saveSync(existing.setName(group.getName())
                                                               .setDescription(group.getDescription())
                                                               .setUpdated(new Date()),
                                                       organizationId));
    }

    @Override
    public Future<Void> deleteGroup(String groupId) {
        Validate.notBlank(groupId, "groupId cannot be blank");
        String organizationId = requireOrgParticipant().getOrganizationId();
        return whileGroupKept(groupId, organizationId, () -> model()
                .compose(model -> relationships.findBindings(SYSTEM, AuthzNameUtil.userset(new Subject(SubjectType.GROUP, groupId)),
                                                             model.tier(AuthzConstants.ORGANIZATION_TYPE)))
                .compose(bindings -> {
                    // a grant is revoked where it was made, so a group a grant still holds stays
                    if (!bindings.isEmpty()) {
                        throw new IllegalStateException("Group " + groupId + " is still held by grants; revoke them first");
                    }
                    return Future.<Void>succeededFuture();
                })
                // its membership first, then the row: a group whose row outlives its members reaches nobody
                .compose(v -> relationships.removeObject(SYSTEM, AuthzNameUtil.object(AuthzConstants.GROUP_TYPE, groupId)))
                .compose(v -> groups.deleteByIdSync(groupId, organizationId)));
    }

    // One caller at a time deletes a group, adds a member to it or grants it a role, so a deletion finds every grant
    // made to the group, and a grant or a member is never written for a group whose row is gone. The group is read
    // once the lock is held
    private <T> Future<T> whileGroupKept(String groupId, String organizationId, Supplier<Future<T>> change) {
        return relationships.exclusively(AuthzNameUtil.object(AuthzConstants.GROUP_TYPE, groupId),
                                         () -> requireGroup(groupId, organizationId).compose(row -> change.get()));
    }

    @Override
    public Future<List<OrganizationParticipantIdentity>> findGroupMembers(String groupId) {
        Validate.notBlank(groupId, "groupId cannot be blank");
        String organizationId = requireOrgParticipant().getOrganizationId();
        return requireGroup(groupId, organizationId)
                .compose(row -> relationships.findMembers(SYSTEM, AuthzNameUtil.object(AuthzConstants.GROUP_TYPE, groupId)))
                .compose(held -> {
                    List<Future<ParticipantIdentity>> members = new ArrayList<>();
                    for (String member : held) {
                        if (AuthzConstants.USER_TYPE.equals(AuthzNameUtil.typeOf(member))) {
                            members.add(identities.findById(AuthzNameUtil.idOf(member)));
                        }
                    }
                    return Future.all(members);
                })
                .map(members -> members.<ParticipantIdentity>list()
                                       .stream()
                                       .filter(OrganizationParticipantIdentity.class::isInstance)
                                       .map(OrganizationParticipantIdentity.class::cast)
                                       .toList());
    }

    @Override
    public Future<Void> addGroupMember(String groupId, String userId) {
        Validate.notBlank(groupId, "groupId cannot be blank");
        Validate.notBlank(userId, "userId cannot be blank");
        String organizationId = requireOrgParticipant().getOrganizationId();
        return whileGroupKept(groupId, organizationId, () -> requireMember(userId, organizationId)
                .compose(v -> relationships.ensure(SYSTEM, List.of(AuthzGraphUtil.groupMember(userId, groupId)))));
    }

    @Override
    public Future<Void> removeGroupMember(String groupId, String userId) {
        Validate.notBlank(groupId, "groupId cannot be blank");
        Validate.notBlank(userId, "userId cannot be blank");
        String organizationId = requireOrgParticipant().getOrganizationId();
        return requireGroup(groupId, organizationId)
                .compose(row -> relationships.remove(SYSTEM, List.of(AuthzGraphUtil.groupMember(userId, groupId))));
    }

    @Override
    public Future<Grant> grant(Subject subject, String roleId, Resource resource) {
        validate(subject);
        Validate.notBlank(roleId, "roleId cannot be blank");
        validate(resource);
        String organizationId = requireOrgParticipant().getOrganizationId();
        return model().compose(model -> requireGrantable(model, roleId, resource.type(), organizationId)
                .compose(v -> requireSubject(subject, organizationId))
                .compose(stored -> requireInOrganization(model, resource, organizationId)
                        .compose(lineage -> roleService.whileRoleKept(RoleOwner.organization(organizationId), model, roleId, () -> {
                            Supplier<Future<String>> bind = () -> relationships.bind(SYSTEM, roleId, AuthzNameUtil.userset(stored),
                                                                                    AuthzGraphUtil.authzObject(organizationId, resource));
                            return stored.type() == SubjectType.GROUP ? whileGroupKept(stored.id(), organizationId, bind) : bind.get();
                        }))))
                .map(grantId -> new Grant(grantId, roleId, subject, resource));
    }

    @Override
    public Future<Void> revoke(Resource resource, String grantId) {
        validate(resource);
        Validate.notBlank(grantId, "grantId cannot be blank");
        String organizationId = requireOrgParticipant().getOrganizationId();
        String object = AuthzGraphUtil.authzObject(organizationId, resource);
        return model().compose(model -> requireInOrganization(model, resource, organizationId))
                .compose(lineage -> machines(List.of(AuthzNameUtil.grantMember(grantId))))
                .compose(machines -> (AuthzConstants.ORGANIZATION_TYPE.equals(resource.type())
                        ? identities.removeAdministrators(object, grant -> grant.id().equals(grantId), OrganizationParticipantIdentity.class,
                                                          () -> relationships.revoke(SYSTEM, grantId, object))
                        : relationships.revoke(SYSTEM, grantId, object))
                        .compose(v -> reconnected(machines)));
    }

    // The members of every grant of a role, on each type the role may be granted on
    private Future<List<String>> holders(String roleId, Set<String> boundOn) {
        return relationships.findBindings(SYSTEM, AuthzNameUtil.roleObject(roleId), boundOn).compose(bindings -> {
            Future<List<String>> ret = Future.succeededFuture(new ArrayList<>());
            for (String binding : bindings) {
                ret = ret.compose(found -> relationships.findMembers(SYSTEM, binding).map(members -> {
                    found.addAll(members);
                    return found;
                }));
            }
            return ret;
        });
    }

    // The ids of the machines among the members of grants
    private Future<List<String>> machines(List<String> members) {
        Future<List<String>> ret = Future.succeededFuture(new ArrayList<>());
        for (String member : members) {
            if (AuthzConstants.USER_TYPE.equals(AuthzNameUtil.typeOf(member))) {
                String id = AuthzNameUtil.idOf(member);
                ret = ret.compose(found -> identities.findById(id).map(identity -> {
                    if (identity instanceof MachineParticipantIdentity) {
                        found.add(id);
                    }
                    return found;
                }));
            }
        }
        return ret;
    }

    // The zones a machine hosts services in are read from its grants when it connects and kept for the connection,
    // so a grant or a role's permission taken from a machine ends its connections and it connects again with the
    // zones it still holds. A user's requests are each checked as they arrive, so its connections stay
    private Future<Void> reconnected(List<String> machines) {
        Future<Void> ret = Future.succeededFuture();
        for (String machine : machines) {
            ret = ret.compose(v -> connectionRevocationService.revoke(machine));
        }
        return ret;
    }

    @Override
    public Future<List<Grant>> findGrants(Resource resource) {
        validate(resource);
        String organizationId = requireOrgParticipant().getOrganizationId();
        return model().compose(model -> requireInOrganization(model, resource, organizationId))
                      .compose(this::grantsOn)
                      .map(grants -> grants.stream().map(AuthzGraphUtil::localGrant).toList());
    }

    @Override
    public Future<List<String>> listAccessible(String type, String permission) {
        return callerPermissions.listAccessible(type, permission);
    }

    @Override
    public Future<AccessExplanation> explain(Subject subject, String permission, Resource resource) {
        validate(subject);
        Validate.notBlank(permission, "permission cannot be blank");
        validate(resource);
        String organizationId = requireOrgParticipant().getOrganizationId();
        String name = AuthzUtil.permissionName(resource.type(), permission);
        return requireSubject(subject, organizationId).compose(stored -> model()
                .compose(model -> requireInOrganization(model, resource, organizationId)
                        .compose(this::grantsOn)
                        .compose(grants -> relationships.explain(SYSTEM, model,
                                                                 new RelationshipTuple(AuthzNameUtil.userset(stored), name, AuthzGraphUtil.authzObject(organizationId, resource)),
                                                                 List.of(), grants)))
                .map(AuthzGraphUtil::localExplanation));
    }

    // The grants made on each resource of a lineage, the resource's own first
    private Future<List<Grant>> grantsOn(List<String> lineage) {
        List<Future<List<Grant>>> perResource = lineage.stream().map(object -> relationships.findGrants(SYSTEM, object)).toList();
        return Future.all(perResource).map(all -> {
            List<Grant> ret = new ArrayList<>();
            for (int i = 0; i < lineage.size(); i++) {
                ret.addAll(all.<List<Grant>>resultAt(i));
            }
            return ret;
        });
    }

    /**
     * The resource's lineage, itself first and then each ancestor up to the caller's organization, which the
     * resource must be in.
     */
    private Future<List<String>> requireInOrganization(AuthzModel model, Resource resource, String organizationId) {
        String organization = AuthzNameUtil.object(AuthzConstants.ORGANIZATION_TYPE, organizationId);
        return relationships.findLineage(SYSTEM, AuthzGraphUtil.authzObject(organizationId, resource), organization, model.parents()).map(lineage -> {
            if (!organization.equals(lineage.getLast())) {
                throw new IllegalArgumentException(AuthzNameUtil.object(resource.type(), resource.id()) + " is not in organization " + organizationId);
            }
            return lineage;
        });
    }

    // A role of the organization's tier, built in or its own, homed at the resource's type or inside it
    private Future<Void> requireGrantable(AuthzModel model, String roleId, String resourceType, String organizationId) {
        Future<Void> ret;
        if (model.roles().containsKey(roleId)) {
            if (!model.tier(AuthzConstants.ORGANIZATION_TYPE).contains(AuthzNameUtil.roleHome(roleId))) {
                throw new IllegalArgumentException("Role " + roleId + " is not a role of the organization");
            }
            ret = Future.succeededFuture();
        } else {
            ret = roleService.requireRole(RoleOwner.organization(organizationId), roleId).mapEmpty();
        }
        return ret.map(v -> {
            model.requireGrantable(roleId, resourceType);
            return null;
        });
    }

    // The subject, once it is known to be a member, a machine or a group of the organization
    private Future<Subject> requireSubject(Subject subject, String organizationId) {
        Future<Subject> ret;
        if (subject.type() == SubjectType.USER) {
            ret = requireUser(subject.id(), organizationId);
        } else {
            ret = requireGroup(subject.id(), organizationId).map(subject);
        }
        return ret;
    }

    // A role of one of the organization's applications is granted in that application's store, never here
    private Future<Group> requireGroup(String groupId, String organizationId) {
        return groups.findById(groupId, organizationId).map(group -> {
            if (group == null) {
                throw new IllegalArgumentException("No group of the organization has id " + groupId);
            }
            return group;
        });
    }

    // A member of the organization or one of its machines, such as a deployment's runtime; an application's user is
    // neither
    private Future<Subject> requireUser(String id, String organizationId) {
        return identities.findById(id).map(identity -> {
            boolean itsMachine = identity instanceof MachineParticipantIdentity machine
                    && organizationId.equals(machine.getOrganizationId())
                    && machine.getApplicationId() == null;
            if (!itsMachine && !isMember(identity, organizationId)) {
                throw new IllegalArgumentException("No member or machine of the organization has id " + id);
            }
            return new Subject(SubjectType.USER, id);
        });
    }

    private Future<Void> requireMember(String organizationUserId, String organizationId) {
        return identities.findById(organizationUserId).map(identity -> {
            if (!isMember(identity, organizationId)) {
                throw new IllegalArgumentException("No member of the organization has id " + organizationUserId);
            }
            return null;
        });
    }

    private static boolean isMember(ParticipantIdentity identity, String organizationId) {
        return identity instanceof OrganizationParticipantIdentity member && organizationId.equals(member.getOrganizationId());
    }

    private Future<AuthzModel> model() {
        return models.findSystemModel();
    }

    private OrganizationParticipant requireOrgParticipant() {
        return securityContext.requireParticipant(OrganizationParticipant.class);
    }

    private static void validate(Subject subject) {
        Validate.notNull(subject, "subject cannot be null");
        Validate.notNull(subject.type(), "subject type cannot be null");
        // tenants and end users are an application's, and so are grants to them
        Validate.isTrue(subject.type() == SubjectType.USER || subject.type() == SubjectType.GROUP,
                        "a grant in an organization is made to a member, a machine or a group");
        Validate.notBlank(subject.id(), "subject id cannot be blank");
    }

    private static void validate(Resource resource) {
        Validate.notNull(resource, "resource cannot be null");
        Validate.notBlank(resource.type(), "resource type cannot be blank");
        Validate.notBlank(resource.id(), "resource id cannot be blank");
    }
}
