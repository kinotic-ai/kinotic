package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectType;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.Group;
import org.kinotic.domain.api.model.security.Role;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.repositories.GroupRepository;
import org.kinotic.domain.api.repositories.RoleRepository;
import org.kinotic.domain.api.services.security.AuthzModelService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
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
import java.util.TreeSet;
import java.util.UUID;

import static org.kinotic.authz.api.services.AuthzStoreService.SYSTEM;

@Component
@RequiredArgsConstructor
public class DefaultPermissionService implements PermissionService {

    // an organization's roles fit one page: the console reads them whole
    private static final int ROLE_PAGE_SIZE = 1000;
    // the root of the tier an organization's members manage: the organization and everything inside it but tenants
    private static final String ORGANIZATION = AuthzConstants.ORGANIZATION_TYPE;

    private final SecurityContext securityContext;
    private final AuthzModelService models;
    private final RelationshipService relationships;
    private final RoleRepository roles;
    private final GroupRepository groups;
    private final ParticipantIdentityService identities;

    @Override
    public Future<Map<String, Set<String>>> findPermissions() {
        requireOrgParticipant();
        return model().map(model -> model.catalog(ORGANIZATION));
    }

    @Override
    public Future<List<RoleDefinition>> findRoles() {
        String organizationId = requireOrgParticipant().getOrganizationId();
        return model().compose(model -> {
            List<RoleDefinition> ret = new ArrayList<>(model.builtInRoles(ORGANIZATION));
            return roles.findAll(organizationId, Pageable.create(0, ROLE_PAGE_SIZE, Sort.by("name")))
                        .compose(page -> Future.all(page.getContent().stream().map(row -> defined(row, model)).toList()))
                        .map(defined -> {
                            ret.addAll(defined.list());
                            return ret;
                        });
        });
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
            String home = role.id() == null ? role.resourceType() : AuthzUtil.roleHome(role.id());
            if (home == null || !model.tier(ORGANIZATION).contains(home)) {
                throw new IllegalArgumentException("A role of the organization is homed at one of " + model.tier(ORGANIZATION)
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
            Future<Role> row = role.id() == null
                    ? Future.succeededFuture(new Role().setId(AuthzUtil.roleId(home, UUID.randomUUID().toString()))
                                                       .setOrganizationId(organizationId)
                                                       .setCreated(new Date()))
                    : requireRole(role.id(), organizationId);
            return row.compose(existing -> {
                existing.setName(role.name()).setDescription(role.description()).setUpdated(new Date());
                // the row first, then its permissions: a role whose permissions failed to write exists and grants nothing
                return roles.saveSync(existing, organizationId)
                            .compose(saved -> relationships.ensureRoles(SYSTEM, Map.of(saved.getId(), role.permissions())).map(saved));
            }).map(saved -> new RoleDefinition(saved.getId(), saved.getName(), saved.getDescription(), false, home,
                                               model.grantableOn(home, ORGANIZATION), Set.copyOf(role.permissions())));
        });
    }

    @Override
    public Future<Void> deleteRole(String roleId) {
        Validate.notBlank(roleId, "roleId cannot be blank");
        String organizationId = requireOrgParticipant().getOrganizationId();
        String role = AuthzUtil.roleObject(roleId);
        return requireRole(roleId, organizationId)
                .compose(row -> model())
                .compose(model -> requireUnused(role, model.grantableOn(AuthzUtil.roleHome(roleId), ORGANIZATION), "Role " + roleId))
                .compose(v -> relationships.read(SYSTEM, role))
                // its permissions first, then the row: a role whose row outlives its permissions grants nothing
                .compose(held -> relationships.remove(SYSTEM, held))
                .compose(v -> roles.deleteByIdSync(roleId, organizationId));
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
        String group = AuthzUtil.object(AuthzConstants.GROUP_TYPE, groupId);
        return requireGroup(groupId, organizationId)
                .compose(row -> model())
                .compose(model -> requireUnused(AuthzGraphUtil.authzSubject(new Subject(SubjectType.GROUP, groupId)), model.tier(ORGANIZATION), "Group " + groupId))
                .compose(v -> relationships.read(SYSTEM, group))
                // its membership first, then the row: a group whose row outlives its members reaches nobody
                .compose(held -> relationships.remove(SYSTEM, held))
                .compose(v -> groups.deleteByIdSync(groupId, organizationId));
    }

    @Override
    public Future<List<OrganizationParticipantIdentity>> findGroupMembers(String groupId) {
        Validate.notBlank(groupId, "groupId cannot be blank");
        String organizationId = requireOrgParticipant().getOrganizationId();
        return requireGroup(groupId, organizationId)
                .compose(row -> relationships.read(SYSTEM, AuthzUtil.object(AuthzConstants.GROUP_TYPE, groupId)))
                .compose(held -> {
                    List<Future<ParticipantIdentity>> members = new ArrayList<>();
                    for (RelationshipTuple tuple : held) {
                        if (AuthzConstants.MEMBER_RELATION.equals(tuple.relation()) && AuthzConstants.USER_TYPE.equals(AuthzUtil.typeOf(tuple.user()))) {
                            members.add(identities.findById(AuthzUtil.idOf(tuple.user())));
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
        return requireGroup(groupId, organizationId)
                .compose(row -> requireMember(userId, organizationId))
                .compose(v -> relationships.ensure(SYSTEM, List.of(membership(AuthzGraphUtil.authzUser(userId), groupId))));
    }

    @Override
    public Future<Void> removeGroupMember(String groupId, String userId) {
        Validate.notBlank(groupId, "groupId cannot be blank");
        Validate.notBlank(userId, "userId cannot be blank");
        String organizationId = requireOrgParticipant().getOrganizationId();
        return requireGroup(groupId, organizationId)
                .compose(row -> relationships.remove(SYSTEM, List.of(membership(AuthzGraphUtil.authzUser(userId), groupId))));
    }

    @Override
    public Future<Grant> grant(Subject subject, String roleId, Resource resource) {
        validate(subject);
        Validate.notBlank(roleId, "roleId cannot be blank");
        validate(resource);
        String organizationId = requireOrgParticipant().getOrganizationId();
        return requireGrantable(roleId, resource.type(), organizationId)
                .compose(v -> requireSubject(subject, organizationId))
                .compose(stored -> requireInOrganization(resource, organizationId)
                        .compose(lineage -> relationships.bind(SYSTEM, roleId, AuthzGraphUtil.authzSubject(stored), AuthzGraphUtil.authzObject(organizationId, resource))))
                .map(bindingId -> new Grant(bindingId, roleId, subject, resource));
    }

    @Override
    public Future<Void> revoke(Resource resource, String grantId) {
        validate(resource);
        Validate.notBlank(grantId, "grantId cannot be blank");
        String organizationId = requireOrgParticipant().getOrganizationId();
        String object = AuthzGraphUtil.authzObject(organizationId, resource);
        return requireInOrganization(resource, organizationId)
                .compose(lineage -> ORGANIZATION.equals(resource.type()) ? requireAnotherAdministrator(object, grantId) : Future.<Void>succeededFuture())
                .compose(v -> relationships.revoke(SYSTEM, grantId, object));
    }

    // Nothing restores an organization's administrator once the last one who can sign in is gone
    private Future<Void> requireAnotherAdministrator(String organization, String grantId) {
        return relationships.findGrants(SYSTEM, organization)
                            .compose(grants -> identities.requireAnotherEnabledHolder(grants.stream()
                                                                                            .filter(grant -> AuthzConstants.ORGANIZATION_ADMIN_ROLE.equals(grant.roleId()))
                                                                                            .toList(),
                                                                                      grantId, OrganizationParticipantIdentity.class));
    }

    @Override
    public Future<List<Grant>> findGrants(Resource resource) {
        validate(resource);
        String organizationId = requireOrgParticipant().getOrganizationId();
        return requireInOrganization(resource, organizationId).compose(this::grantsOn)
                                                              .map(grants -> grants.stream().map(AuthzGraphUtil::localGrant).toList());
    }

    @Override
    public Future<List<String>> listAccessible(String type, String permission) {
        Validate.notBlank(type, "type cannot be blank");
        Validate.notBlank(permission, "permission cannot be blank");
        OrganizationParticipant participant = requireOrgParticipant();
        if (!AuthzGraphUtil.withinDelegateCeiling(participant, type, permission)) {
            return Future.succeededFuture(List.of());
        }
        return relationships.listObjects(SYSTEM,
                                         AuthzGraphUtil.authzUser(participant),
                                         AuthzUtil.permissionName(type, permission), type,
                                         Consistency.MINIMIZE_LATENCY)
                            .map(objects -> objects.stream().map(object -> AuthzGraphUtil.localId(type, AuthzUtil.idOf(object))).toList());
    }

    /**
     * Whether the caller holds a permission on one resource of its organization, through any grant that reaches it.
     *
     * @param type       the resource type
     * @param permission the permission's short name, such as {@code can_read}
     * @param id         the resource's id within the caller's organization
     * @return true when the caller holds the permission on the resource
     */
    public Future<Boolean> holds(String type, String permission, String id) {
        Validate.notBlank(type, "type cannot be blank");
        Validate.notBlank(permission, "permission cannot be blank");
        Validate.notBlank(id, "id cannot be blank");
        OrganizationParticipant participant = requireOrgParticipant();
        return callerHolds(participant, type, permission, AuthzGraphUtil.authzObject(participant.getOrganizationId(), new Resource(type, id)));
    }

    /**
     * The resources of a type the caller holds a permission on, as {@link #listAccessible} lists them, or null when
     * the caller holds the permission on its organization, and so on every resource of the type in it.
     *
     * @param type       the resource type
     * @param permission the permission's short name, such as {@code can_read}
     * @return the ids of the resources, or null for every one in the caller's organization
     */
    public Future<List<String>> findAccessibleIds(String type, String permission) {
        Validate.notBlank(type, "type cannot be blank");
        Validate.notBlank(permission, "permission cannot be blank");
        OrganizationParticipant participant = requireOrgParticipant();
        // a caller holding the permission throughout the organization is answered with one check, without a listing
        return callerHolds(participant, type, permission, AuthzUtil.object(ORGANIZATION, participant.getOrganizationId()))
                .compose(held -> held ? Future.succeededFuture(null) : listAccessible(type, permission));
    }

    // Whether the caller holds the permission on the object; a delegate holds only what its type's ceiling admits
    private Future<Boolean> callerHolds(OrganizationParticipant participant, String type, String permission, String object) {
        Future<Boolean> ret;
        if (AuthzGraphUtil.withinDelegateCeiling(participant, type, permission)) {
            RelationshipTuple held = new RelationshipTuple(AuthzGraphUtil.authzUser(participant), AuthzUtil.permissionName(type, permission), object);
            ret = relationships.check(SYSTEM, held, Consistency.MINIMIZE_LATENCY);
        } else {
            ret = Future.succeededFuture(false);
        }
        return ret;
    }

    @Override
    public Future<AccessExplanation> explain(Subject subject, String permission, Resource resource) {
        validate(subject);
        Validate.notBlank(permission, "permission cannot be blank");
        validate(resource);
        String organizationId = requireOrgParticipant().getOrganizationId();
        String name = AuthzUtil.permissionName(resource.type(), permission);
        return requireSubject(subject, organizationId).compose(stored -> requireInOrganization(resource, organizationId)
                .compose(this::grantsOn)
                .compose(grants -> relationships.explain(SYSTEM, stored,
                                                         new RelationshipTuple(AuthzGraphUtil.authzSubject(stored), name, AuthzGraphUtil.authzObject(organizationId, resource)),
                                                         List.of(), grants))
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
    private Future<List<String>> requireInOrganization(Resource resource, String organizationId) {
        String organization = AuthzUtil.object(AuthzConstants.ORGANIZATION_TYPE, organizationId);
        return lineage(AuthzGraphUtil.authzObject(organizationId, resource), new ArrayList<>()).map(lineage -> {
            int within = lineage.indexOf(organization);
            if (within < 0) {
                throw new IllegalArgumentException(AuthzUtil.object(resource.type(), resource.id()) + " is not in organization " + organizationId);
            }
            return lineage.subList(0, within + 1);
        });
    }

    // The containment tuple of an object names its parent's type as the relation and the parent as the user; a
    // binding's attachment names the binding's type the same way and is not one
    private Future<List<String>> lineage(String object, List<String> collected) {
        collected.add(object);
        return relationships.read(SYSTEM, object).compose(held -> {
            String parent = null;
            for (RelationshipTuple tuple : held) {
                if (!AuthzConstants.ROLE_BINDING_RELATION.equals(tuple.relation())
                        && tuple.relation().equals(AuthzUtil.typeOf(tuple.user()))) {
                    parent = tuple.user();
                    break;
                }
            }
            return parent == null ? Future.succeededFuture(collected) : lineage(parent, collected);
        });
    }

    // A role of the organization's tier, built in or its own, homed at the resource's type or inside it
    private Future<Void> requireGrantable(String roleId, String resourceType, String organizationId) {
        return model().compose(model -> {
            Future<Void> ret;
            if (model.roles().containsKey(roleId)) {
                if (!model.tier(ORGANIZATION).contains(AuthzUtil.roleHome(roleId))) {
                    throw new IllegalArgumentException("Role " + roleId + " is not a role of the organization");
                }
                ret = Future.succeededFuture();
            } else {
                ret = requireRole(roleId, organizationId).mapEmpty();
            }
            return ret.map(v -> {
                if (!model.grantable(roleId, resourceType)) {
                    throw new IllegalArgumentException("Role " + roleId + " is homed at " + AuthzUtil.roleHome(roleId)
                                                               + " and cannot be granted on a " + resourceType);
                }
                return null;
            });
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

    private Future<Role> requireRole(String roleId, String organizationId) {
        return roles.findById(roleId, organizationId).map(role -> {
            if (role == null) {
                throw new IllegalArgumentException("No role of the organization has id " + roleId);
            }
            return role;
        });
    }

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
        return identities.findById(id).compose(identity -> {
            Future<Subject> ret;
            if (identity instanceof MachineParticipantIdentity machine
                    && organizationId.equals(machine.getOrganizationId())
                    && machine.getApplicationId() == null) {
                ret = Future.succeededFuture(new Subject(SubjectType.USER, id));
            } else if (identity instanceof OrganizationParticipantIdentity member
                    && organizationId.equals(member.getOrganizationId())) {
                ret = Future.succeededFuture(new Subject(SubjectType.USER, id));
            } else {
                throw new IllegalArgumentException("No member or machine of the organization has id " + id);
            }
            return ret;
        });
    }

    private Future<Void> requireMember(String organizationUserId, String organizationId) {
        return identities.findById(organizationUserId).map(identity -> {
            if (!(identity instanceof OrganizationParticipantIdentity member) || !organizationId.equals(member.getOrganizationId())) {
                throw new IllegalArgumentException("No member of the organization has id " + organizationUserId);
            }
            return null;
        });
    }

    // A grant is revoked where it was made, so a role or group a grant still holds stays; its bindings are read
    // on each type it may be granted on, since a binding is typed by what it is attached to
    private Future<Void> requireUnused(String user, Set<String> boundOn, String what) {
        List<Future<List<RelationshipTuple>>> reads = boundOn.stream()
                                                             .map(type -> relationships.readByUser(SYSTEM, user, AuthzUtil.roleBindingType(type)))
                                                             .toList();
        return Future.all(reads).map(all -> {
            int bindings = 0;
            for (int i = 0; i < reads.size(); i++) {
                bindings += all.<List<RelationshipTuple>>resultAt(i).size();
            }
            if (bindings > 0) {
                throw new IllegalStateException(what + " is held by " + bindings + " grant(s); revoke them first");
            }
            return null;
        });
    }

    private Future<RoleDefinition> defined(Role role, AuthzModel model) {
        String home = AuthzUtil.roleHome(role.getId());
        return relationships.read(SYSTEM, AuthzUtil.roleObject(role.getId())).map(held -> {
            Set<String> permissions = new TreeSet<>();
            for (RelationshipTuple tuple : held) {
                if (AuthzConstants.EVERYONE.equals(tuple.user())) {
                    permissions.add(tuple.relation());
                }
            }
            return new RoleDefinition(role.getId(), role.getName(), role.getDescription(), false, home,
                                      model.grantableOn(home, ORGANIZATION), permissions);
        });
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
        Validate.notBlank(subject.id(), "subject id cannot be blank");
    }

    private static void validate(Resource resource) {
        Validate.notNull(resource, "resource cannot be null");
        Validate.notBlank(resource.type(), "resource type cannot be blank");
        Validate.notBlank(resource.id(), "resource id cannot be blank");
    }

    private static RelationshipTuple membership(String member, String groupId) {
        return new RelationshipTuple(member,
                                     AuthzConstants.MEMBER_RELATION,
                                     AuthzUtil.object(AuthzConstants.GROUP_TYPE, groupId));
    }
}
