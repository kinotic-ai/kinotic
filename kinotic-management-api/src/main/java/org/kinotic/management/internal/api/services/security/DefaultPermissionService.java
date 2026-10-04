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
import org.kinotic.authz.api.model.SubjectKind;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.Group;
import org.kinotic.domain.api.model.security.Role;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.repositories.GroupRepository;
import org.kinotic.domain.api.repositories.RoleRepository;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.services.security.PermissionService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import static org.kinotic.authz.api.services.AuthzStoreService.PLATFORM;

@Component
@RequiredArgsConstructor
public class DefaultPermissionService implements PermissionService {

    // an organization's roles fit one page: the console reads them whole
    private static final int ROLE_PAGE_SIZE = 1000;

    private final SecurityContext securityContext;
    private final ServiceDirectory directory;
    private final AuthzModelGenerator generator;
    private final AuthzStoreService stores;
    private final RelationshipService relationships;
    private final RoleRepository roles;
    private final GroupRepository groups;
    private final ParticipantIdentityService identities;

    @Override
    public Future<Map<String, Set<String>>> findPermissions() {
        requireOrgParticipant();
        return model().map(model -> {
            Map<String, Set<String>> ret = new TreeMap<>();
            model.permissions().forEach((type, permissions) -> {
                Set<String> names = new TreeSet<>();
                for (String permission : permissions) {
                    names.add(AuthzUtil.permissionName(type, permission));
                }
                ret.put(type, names);
            });
            return ret;
        });
    }

    @Override
    public Future<List<RoleDefinition>> findRoles() {
        String organizationId = requireOrgParticipant().getOrganizationId();
        return model().compose(model -> {
            List<RoleDefinition> ret = new ArrayList<>();
            model.roles().forEach((id, permissions) -> ret.add(new RoleDefinition(id, AuthzUtil.builtInRoleName(id), null, true, permissions)));
            return roles.findAll(organizationId, Pageable.create(0, ROLE_PAGE_SIZE, Sort.by("name")))
                        .compose(page -> Future.all(page.getContent().stream().map(this::defined).toList()))
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
            Set<String> catalog = new HashSet<>();
            model.permissions().forEach((type, permissions) -> permissions.forEach(permission -> catalog.add(AuthzUtil.permissionName(type, permission))));
            for (String permission : role.permissions()) {
                if (!catalog.contains(permission)) {
                    throw new IllegalArgumentException("No permission is named '" + permission + "'");
                }
            }
            return role.id() == null
                    ? Future.succeededFuture(new Role().setId(UUID.randomUUID().toString()).setOrganizationId(organizationId).setCreated(new Date()))
                    : requireRole(role.id(), organizationId);
        }).compose(row -> {
            row.setName(role.name()).setDescription(role.description()).setUpdated(new Date());
            // the row first, then its permissions: a role whose permissions failed to write exists and grants nothing
            return roles.saveSync(row, organizationId)
                        .compose(saved -> relationships.ensureRoles(PLATFORM, Map.of(saved.getId(), role.permissions())).map(saved));
        }).map(row -> new RoleDefinition(row.getId(), row.getName(), row.getDescription(), false, Set.copyOf(role.permissions())));
    }

    @Override
    public Future<Void> deleteRole(String roleId) {
        Validate.notBlank(roleId, "roleId cannot be blank");
        String organizationId = requireOrgParticipant().getOrganizationId();
        String role = AuthzUtil.object(AuthzUtil.ROLE_TYPE, roleId);
        return requireRole(roleId, organizationId)
                .compose(row -> requireUnused(role, "Role " + roleId))
                .compose(v -> relationships.read(PLATFORM, role))
                // its permissions first, then the row: a role whose row outlives its permissions grants nothing
                .compose(held -> relationships.remove(PLATFORM, held))
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
        String group = AuthzUtil.object(AuthzUtil.GROUP_TYPE, groupId);
        return requireGroup(groupId, organizationId)
                .compose(row -> requireUnused(membersOf(group), "Group " + groupId))
                .compose(v -> relationships.read(PLATFORM, group))
                // its membership first, then the row: a group whose row outlives its members reaches nobody
                .compose(held -> relationships.remove(PLATFORM, held))
                .compose(v -> groups.deleteByIdSync(groupId, organizationId));
    }

    @Override
    public Future<List<UserParticipantIdentity>> findGroupMembers(String groupId) {
        Validate.notBlank(groupId, "groupId cannot be blank");
        String organizationId = requireOrgParticipant().getOrganizationId();
        return requireGroup(groupId, organizationId)
                .compose(row -> relationships.read(PLATFORM, AuthzUtil.object(AuthzUtil.GROUP_TYPE, groupId)))
                .compose(held -> {
                    List<Future<ParticipantIdentity>> members = new ArrayList<>();
                    for (RelationshipTuple tuple : held) {
                        if (AuthzUtil.MEMBER_RELATION.equals(tuple.relation()) && AuthzUtil.USER_TYPE.equals(AuthzUtil.typeOf(tuple.user()))) {
                            members.add(identities.findById(AuthzUtil.idOf(tuple.user())));
                        }
                    }
                    return Future.all(members);
                })
                .map(members -> members.<ParticipantIdentity>list()
                                       .stream()
                                       .filter(UserParticipantIdentity.class::isInstance)
                                       .map(UserParticipantIdentity.class::cast)
                                       .toList());
    }

    @Override
    public Future<Void> addGroupMember(String groupId, String userId) {
        Validate.notBlank(groupId, "groupId cannot be blank");
        Validate.notBlank(userId, "userId cannot be blank");
        String organizationId = requireOrgParticipant().getOrganizationId();
        return requireGroup(groupId, organizationId)
                .compose(row -> requireMember(userId, organizationId))
                .compose(member -> relationships.ensure(PLATFORM, List.of(membership(userId, groupId))));
    }

    @Override
    public Future<Void> removeGroupMember(String groupId, String userId) {
        Validate.notBlank(groupId, "groupId cannot be blank");
        Validate.notBlank(userId, "userId cannot be blank");
        String organizationId = requireOrgParticipant().getOrganizationId();
        return requireGroup(groupId, organizationId)
                .compose(row -> relationships.remove(PLATFORM, List.of(membership(userId, groupId))));
    }

    @Override
    public Future<Grant> grant(Subject subject, String roleId, Resource resource) {
        validate(subject);
        Validate.notBlank(roleId, "roleId cannot be blank");
        validate(resource);
        String organizationId = requireOrgParticipant().getOrganizationId();
        return requireGrantable(roleId, organizationId)
                .compose(v -> requireSubject(subject, organizationId))
                .compose(v -> requireInOrganization(resource, organizationId))
                .compose(lineage -> relationships.bind(PLATFORM, roleId, userOf(subject), objectOf(resource)))
                .map(bindingId -> new Grant(bindingId, roleId, subject, resource));
    }

    @Override
    public Future<Void> revoke(Resource resource, String grantId) {
        validate(resource);
        Validate.notBlank(grantId, "grantId cannot be blank");
        String organizationId = requireOrgParticipant().getOrganizationId();
        return requireInOrganization(resource, organizationId)
                .compose(lineage -> relationships.revoke(PLATFORM, grantId, objectOf(resource)));
    }

    @Override
    public Future<List<Grant>> findGrants(Resource resource) {
        validate(resource);
        String organizationId = requireOrgParticipant().getOrganizationId();
        return requireInOrganization(resource, organizationId).compose(this::grantsOn);
    }

    @Override
    public Future<List<String>> listAccessible(String type, String permission) {
        Validate.notBlank(type, "type cannot be blank");
        Validate.notBlank(permission, "permission cannot be blank");
        OrganizationParticipant participant = requireOrgParticipant();
        return stores.modelId(PLATFORM)
                     .compose(modelId -> relationships.listObjects(PLATFORM, modelId,
                                                                   AuthzUtil.object(AuthzUtil.USER_TYPE, participant.getId()),
                                                                   AuthzUtil.permissionName(type, permission), type,
                                                                   Consistency.MINIMIZE_LATENCY))
                     .map(objects -> objects.stream().map(AuthzUtil::idOf).toList());
    }

    @Override
    public Future<AccessExplanation> explain(Subject subject, String permission, Resource resource) {
        validate(subject);
        Validate.notBlank(permission, "permission cannot be blank");
        validate(resource);
        String organizationId = requireOrgParticipant().getOrganizationId();
        String user = userOf(subject);
        String name = AuthzUtil.permissionName(resource.type(), permission);
        return requireSubject(subject, organizationId)
                .compose(v -> requireInOrganization(resource, organizationId))
                .compose(this::grantsOn)
                .compose(grants -> stores.modelId(PLATFORM).compose(modelId -> {
                    // an admin asks after changing access, so the answer must not predate the change
                    Future<Boolean> allowed = relationships.check(PLATFORM, modelId, new RelationshipTuple(user, name, objectOf(resource)),
                                                                  Consistency.HIGHER_CONSISTENCY);
                    List<Future<Boolean>> explains = grants.stream().map(grant -> relationships.explains(PLATFORM, modelId, grant, subject, name)).toList();
                    return Future.all(explains).compose(results -> allowed.map(held -> {
                        List<Grant> through = new ArrayList<>();
                        for (int i = 0; i < grants.size(); i++) {
                            if (results.<Boolean>resultAt(i)) {
                                through.add(grants.get(i));
                            }
                        }
                        return new AccessExplanation(held, through);
                    }));
                }));
    }

    // The grants made on each resource of a lineage, the resource's own first
    private Future<List<Grant>> grantsOn(List<String> lineage) {
        List<Future<List<Grant>>> perResource = lineage.stream().map(object -> relationships.findGrants(PLATFORM, object)).toList();
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
        String organization = AuthzUtil.object(AuthzUtil.ORGANIZATION_TYPE, organizationId);
        return lineage(objectOf(resource), new ArrayList<>()).map(lineage -> {
            int within = lineage.indexOf(organization);
            if (within < 0) {
                throw new IllegalArgumentException(objectOf(resource) + " is not in organization " + organizationId);
            }
            return lineage.subList(0, within + 1);
        });
    }

    // The containment tuple of an object names its parent's type as the relation and the parent as the user; a
    // binding's attachment names the binding's type the same way and is not one
    private Future<List<String>> lineage(String object, List<String> collected) {
        collected.add(object);
        return relationships.read(PLATFORM, object).compose(held -> {
            String parent = null;
            for (RelationshipTuple tuple : held) {
                if (!AuthzUtil.ROLE_BINDING_RELATION.equals(tuple.relation())
                        && tuple.relation().equals(AuthzUtil.typeOf(tuple.user()))) {
                    parent = tuple.user();
                    break;
                }
            }
            return parent == null ? Future.succeededFuture(collected) : lineage(parent, collected);
        });
    }

    private Future<Void> requireGrantable(String roleId, String organizationId) {
        return model().compose(model -> model.roles().containsKey(roleId)
                ? Future.succeededFuture()
                : requireRole(roleId, organizationId).mapEmpty());
    }

    private Future<Void> requireSubject(Subject subject, String organizationId) {
        Future<?> ret;
        if (subject.kind() == SubjectKind.USER) {
            ret = requireMember(subject.id(), organizationId);
        } else {
            ret = requireGroup(subject.id(), organizationId);
        }
        return ret.mapEmpty();
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

    private Future<UserParticipantIdentity> requireMember(String userId, String organizationId) {
        return identities.findById(userId).map(identity -> {
            if (!(identity instanceof UserParticipantIdentity user)
                    || !organizationId.equals(user.getOrganizationId())
                    || user.getApplicationId() != null) {
                throw new IllegalArgumentException("No member of the organization has id " + userId);
            }
            return user;
        });
    }

    // A grant is revoked where it was made, so a role or group a grant still holds stays
    private Future<Void> requireUnused(String user, String what) {
        return relationships.readByUser(PLATFORM, user, AuthzUtil.ROLE_BINDING_TYPE).map(bindings -> {
            if (!bindings.isEmpty()) {
                throw new IllegalStateException(what + " is held by " + bindings.size() + " grant(s); revoke them first");
            }
            return null;
        });
    }

    private Future<RoleDefinition> defined(Role role) {
        return relationships.read(PLATFORM, AuthzUtil.object(AuthzUtil.ROLE_TYPE, role.getId())).map(held -> {
            Set<String> permissions = new TreeSet<>();
            for (RelationshipTuple tuple : held) {
                if (AuthzUtil.EVERYONE.equals(tuple.user())) {
                    permissions.add(tuple.relation());
                }
            }
            return new RoleDefinition(role.getId(), role.getName(), role.getDescription(), false, permissions);
        });
    }

    private Future<AuthzModel> model() {
        return directory.findSystemContracts().map(generator::platformModel);
    }

    private OrganizationParticipant requireOrgParticipant() {
        return securityContext.requireParticipant(OrganizationParticipant.class);
    }

    private static void validate(Subject subject) {
        Validate.notNull(subject, "subject cannot be null");
        Validate.notNull(subject.kind(), "subject kind cannot be null");
        Validate.notBlank(subject.id(), "subject id cannot be blank");
    }

    private static void validate(Resource resource) {
        Validate.notNull(resource, "resource cannot be null");
        Validate.notBlank(resource.type(), "resource type cannot be blank");
        Validate.notBlank(resource.id(), "resource id cannot be blank");
    }

    private static String objectOf(Resource resource) {
        return AuthzUtil.object(resource.type(), resource.id());
    }

    private static String userOf(Subject subject) {
        return subject.kind() == SubjectKind.USER
                ? AuthzUtil.object(AuthzUtil.USER_TYPE, subject.id())
                : membersOf(AuthzUtil.object(AuthzUtil.GROUP_TYPE, subject.id()));
    }

    private static String membersOf(String group) {
        return group + "#" + AuthzUtil.MEMBER_RELATION;
    }

    private static RelationshipTuple membership(String userId, String groupId) {
        return new RelationshipTuple(AuthzUtil.object(AuthzUtil.USER_TYPE, userId),
                                     AuthzUtil.MEMBER_RELATION,
                                     AuthzUtil.object(AuthzUtil.GROUP_TYPE, groupId));
    }
}
