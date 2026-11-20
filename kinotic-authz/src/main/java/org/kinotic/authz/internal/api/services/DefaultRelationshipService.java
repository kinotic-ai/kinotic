package org.kinotic.authz.internal.api.services;

import dev.openfga.sdk.api.model.CheckRequest;
import dev.openfga.sdk.api.model.CheckRequestTupleKey;
import dev.openfga.sdk.api.model.CheckResponse;
import dev.openfga.sdk.api.model.ConsistencyPreference;
import dev.openfga.sdk.api.model.ContextualTupleKeys;
import dev.openfga.sdk.api.model.ListObjectsRequest;
import dev.openfga.sdk.api.model.ReadRequestTupleKey;
import dev.openfga.sdk.api.model.RelationshipCondition;
import dev.openfga.sdk.api.model.Tuple;
import dev.openfga.sdk.api.model.TupleKey;
import dev.openfga.sdk.api.model.TupleKeyWithoutCondition;
import dev.openfga.sdk.errors.ApiException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.model.AccessExplanation;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RolePermissions;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectType;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.Set;
import java.util.TreeSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultRelationshipService implements RelationshipService {

    private static final int HTTP_CONFLICT = 409;
    private static final String OBJECT_LOCK_PREFIX = "kinotic.authz.object.";
    private static final long OBJECT_LOCK_TIMEOUT_MILLIS = 30_000;
    // the most requests one operation has under way at the engine; the client opens a connection for each
    private static final int MAX_CONCURRENT_REQUESTS = 16;
    // The engine stores up to 32,768 bytes of condition context in a tuple. The definitions one permission names
    // are spread over parts of a third of that on average, since hashing fills the parts unevenly.
    private static final int PART_CONTEXT_BYTES = 11_000;
    // what the engine counts for a definition named in a tuple, beside the bytes of its id
    private static final int NAMED_DEFINITION_BYTES = 8;

    private final OpenFgaService fga;
    private final DefaultAuthzStoreService stores;
    private final Vertx vertx;

    // Adds and removes relationships, in as many requests as the engine's limit per request takes
    private Future<Void> write(String store, List<RelationshipTuple> writes, List<RelationshipTuple> deletes) {
        // the engine refuses a request that names a tuple twice, as tuples gathered from several records can, such
        // as the tenant every user of it places under the application
        List<TupleKey> writeKeys = new ArrayList<>();
        for (RelationshipTuple tuple : new LinkedHashSet<>(writes)) {
            writeKeys.add(new TupleKey().user(tuple.user()).relation(tuple.relation())._object(tuple.object()));
        }
        List<TupleKeyWithoutCondition> deleteKeys = new ArrayList<>();
        for (RelationshipTuple tuple : new LinkedHashSet<>(deletes)) {
            deleteKeys.add(new TupleKeyWithoutCondition().user(tuple.user()).relation(tuple.relation())._object(tuple.object()));
        }
        return writeKeys.isEmpty() && deleteKeys.isEmpty()
                ? Future.succeededFuture()
                : stores.storeIdOf(store).compose(id -> fga.write(id, writeKeys, deleteKeys));
    }

    @Override
    public Future<List<RelationshipTuple>> read(String store, String object) {
        return read(store, new ReadRequestTupleKey()._object(object));
    }

    @Override
    public Future<List<RelationshipTuple>> read(String store, String object, String relation) {
        return read(store, new ReadRequestTupleKey()._object(object).relation(relation));
    }

    @Override
    public Future<List<RelationshipTuple>> readByUser(String store, String user, String objectType) {
        // the engine reads by user within one object type, named by the type alone
        return read(store, new ReadRequestTupleKey().user(user)._object(objectType + ":"));
    }

    @Override
    public Future<RolePermissions> findRolePermissions(String store, String roleId) {
        return stores.storeIdOf(store).compose(id -> readRole(id, roleId)).map(HeldRole::permissions);
    }

    // Every relation of a role is a permission it bundles for everything, but the one it holds its parts by; every
    // relation of a part is a permission the role bundles for the definitions the part's tuple names
    private Future<HeldRole> readRole(String storeId, String roleId) {
        String role = AuthzUtil.roleObject(roleId);
        return fga.read(storeId, new ReadRequestTupleKey()._object(role)).compose(held -> {
            Set<String> everywhere = new TreeSet<>();
            List<String> parts = new ArrayList<>();
            for (Tuple tuple : held) {
                if (DefaultAuthzModelGenerator.PART.equals(tuple.getKey().getRelation())) {
                    parts.add(tuple.getKey().getUser());
                } else {
                    everywhere.add(tuple.getKey().getRelation());
                }
            }
            return requestEach(parts, part -> fga.read(storeId, new ReadRequestTupleKey()._object(part))).map(ofParts -> {
                Map<String, Set<String>> named = new TreeMap<>();
                List<TupleKeyWithoutCondition> partTuples = new ArrayList<>();
                for (int i = 0; i < parts.size(); i++) {
                    partTuples.add(new TupleKeyWithoutCondition().user(parts.get(i)).relation(DefaultAuthzModelGenerator.PART)._object(role));
                    for (Tuple tuple : ofParts.get(i)) {
                        String permission = tuple.getKey().getRelation();
                        partTuples.add(new TupleKeyWithoutCondition().user(tuple.getKey().getUser()).relation(permission)._object(parts.get(i)));
                        Object definitions = ((Map<?, ?>) tuple.getKey().getCondition().getContext()).get(DefaultAuthzModelGenerator.NAMED_DEFINITIONS);
                        for (Object definition : ((Map<?, ?>) definitions).keySet()) {
                            named.computeIfAbsent(String.valueOf(definition), k -> new TreeSet<>()).add(permission);
                        }
                    }
                }
                return new HeldRole(new RolePermissions(everywhere, named), partTuples);
            });
        });
    }

    @Override
    public Future<List<String>> findBindings(String store, String user, Set<String> boundOn) {
        // a binding is typed by what it is attached to, so it is read on each type
        return requestEach(List.copyOf(boundOn), type -> readByUser(store, user, AuthzUtil.roleBindingType(type)))
                .map(reads -> reads.stream().flatMap(List::stream).map(RelationshipTuple::object).toList());
    }

    @Override
    public Future<List<String>> findMembers(String store, String object) {
        return read(store, object, AuthzConstants.MEMBER_RELATION).map(held -> held.stream().map(RelationshipTuple::user).toList());
    }

    @Override
    public Future<List<String>> findLineage(String store, String object, String upTo, Map<String, String> parents) {
        return lineage(store, object, upTo, parents, new ArrayList<>());
    }

    // An object holds its parent under the relation named after the parent's type, and that relation alone is read:
    // an application and an organization each hold a tuple per member beside it
    private Future<List<String>> lineage(String store, String object, String upTo, Map<String, String> parents, List<String> collected) {
        collected.add(object);
        String parentType = parents.get(AuthzUtil.typeOf(object));
        Future<List<String>> ret;
        if (object.equals(upTo) || parentType == null) {
            ret = Future.succeededFuture(collected);
        } else {
            ret = read(store, object, parentType).compose(held -> held.isEmpty()
                    ? Future.succeededFuture(collected)
                    : lineage(store, held.getFirst().user(), upTo, parents, collected));
        }
        return ret;
    }

    @Override
    public Future<List<String>> findContained(String store, String parent, String type) {
        String containment = AuthzUtil.typeOf(parent);
        return readByUser(store, parent, type).map(held -> held.stream()
                                                               .filter(tuple -> containment.equals(tuple.relation()))
                                                               .map(RelationshipTuple::object)
                                                               .toList());
    }

    @Override
    public Future<Boolean> holds(String store, RelationshipTuple relationship) {
        return read(store, new ReadRequestTupleKey().user(relationship.user())
                                                    .relation(relationship.relation())
                                                    ._object(relationship.object()))
                .map(tuples -> !tuples.isEmpty());
    }

    @Override
    public Future<Void> ensure(String store, List<RelationshipTuple> relationships) {
        return converging(() -> write(store, relationships, List.of()));
    }

    @Override
    public Future<Void> remove(String store, List<RelationshipTuple> relationships) {
        return converging(() -> write(store, List.of(), relationships));
    }

    // A write the engine refuses as a conflict was raced by another node bringing the same tuples to the same
    // state, as the seed job and a caller creating the application do when the job reaches an application still
    // being provisioned, so it is made again; a second conflict is reported
    private static Future<Void> converging(Supplier<Future<Void>> attempt) {
        return attempt.get().recover(e -> isConflict(e) ? attempt.get() : Future.failedFuture(e));
    }

    private static boolean isConflict(Throwable e) {
        return e instanceof ApiException api && api.getStatusCode() == HTTP_CONFLICT;
    }

    @Override
    public Future<Void> ensureRoles(String store, Map<String, Set<String>> roles) {
        Map<String, RolePermissions> bundled = new HashMap<>();
        roles.forEach((roleId, permissions) -> bundled.put(roleId, new RolePermissions(permissions)));
        return converging(() -> reconcileRoles(store, bundled));
    }

    @Override
    public Future<Void> ensureRole(String store, String roleId, RolePermissions permissions) {
        return converging(() -> reconcileRoles(store, Map.of(roleId, permissions)));
    }

    @Override
    public Future<String> ensureModelWithRoles(String store, AuthzModel model) {
        // A check that runs while a role's tuples are being written can cache the role's answer from before the
        // write, valid for the engine's cache lifetime; the roles are written while no version yet grants through
        // their new relations, so no check against the model can read them before they are in step
        return stores.ensureRoleRelations(store, model)
                     .compose(v -> ensureRoles(store, model.roles()))
                     .compose(v -> stores.ensureModel(store, model));
    }

    private Future<Void> reconcileRoles(String store, Map<String, RolePermissions> roles) {
        List<String> ids = new ArrayList<>(roles.keySet());
        return stores.storeIdOf(store).compose(storeId -> requestEach(ids, roleId -> readRole(storeId, roleId)).compose(held -> {
            List<TupleKey> writes = new ArrayList<>();
            List<TupleKeyWithoutCondition> deletes = new ArrayList<>();
            List<List<TupleKey>> parts = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                String role = AuthzUtil.roleObject(ids.get(i));
                RolePermissions desired = roles.get(ids.get(i));
                RolePermissions bundled = held.get(i).permissions();
                for (String permission : bundled.permissions()) {
                    if (!desired.permissions().contains(permission)) {
                        deletes.add(new TupleKeyWithoutCondition().user(AuthzConstants.EVERYONE).relation(permission)._object(role));
                    }
                }
                for (String permission : desired.permissions()) {
                    if (!bundled.permissions().contains(permission)) {
                        writes.add(new TupleKey().user(AuthzConstants.EVERYONE).relation(permission)._object(role));
                    }
                }
                Map<String, Set<String>> named = namedAlone(desired);
                // a definition falls in a part by the number of parts, so a change to what a role names is every
                // part deleted and written again
                if (!named.equals(bundled.definitionPermissions())) {
                    deletes.addAll(held.get(i).partTuples());
                    parts.addAll(partsOf(role, named));
                }
            }
            // every delete is made before the writes, so a role is never read holding what it held and what it is
            // to hold together; a part is written whole in a request of its own, which keeps each request small
            Future<Void> ret = fga.write(storeId, List.of(), deletes).compose(v -> fga.write(storeId, writes, List.of()));
            for (List<TupleKey> part : parts) {
                ret = ret.compose(v -> fga.write(storeId, part, List.of()));
            }
            return ret;
        }));
    }

    // The permissions a role holds on each definition it names and does not hold for everything
    private static Map<String, Set<String>> namedAlone(RolePermissions permissions) {
        Map<String, Set<String>> ret = new TreeMap<>();
        permissions.definitionPermissions().forEach((definition, named) -> {
            Set<String> alone = new TreeSet<>(named);
            alone.removeAll(permissions.permissions());
            if (!alone.isEmpty()) {
                ret.put(definition, alone);
            }
        });
        return ret;
    }

    // The tuples of each part of a role: one per permission, naming the definitions that fall in the part and hold
    // it, and the one the role holds the part by. The definitions are spread over the parts by their hash, which a
    // check carries, so a check reads the one part its definition falls in
    private static List<List<TupleKey>> partsOf(String role, Map<String, Set<String>> named) {
        Map<String, Integer> contextBytes = new HashMap<>();
        named.forEach((definition, permissions) -> permissions.forEach(
                permission -> contextBytes.merge(permission, definition.getBytes(StandardCharsets.UTF_8).length + NAMED_DEFINITION_BYTES, Integer::sum)));
        int largest = contextBytes.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int count = Math.max(1, (largest + PART_CONTEXT_BYTES - 1) / PART_CONTEXT_BYTES);
        Map<Integer, Map<String, Map<String, Boolean>>> definitionsByPart = new TreeMap<>();
        named.forEach((definition, permissions) -> {
            Map<String, Map<String, Boolean>> ofPart = definitionsByPart.computeIfAbsent((int) (definitionHash(definition) % count), k -> new TreeMap<>());
            permissions.forEach(permission -> ofPart.computeIfAbsent(permission, k -> new TreeMap<>()).put(definition, true));
        });
        List<List<TupleKey>> ret = new ArrayList<>();
        definitionsByPart.forEach((index, ofPart) -> {
            String part = AuthzUtil.object(DefaultAuthzModelGenerator.ROLE_PART, AuthzUtil.idOf(role) + "/" + index);
            List<TupleKey> tuples = new ArrayList<>();
            ofPart.forEach((permission, definitions) -> tuples.add(
                    new TupleKey().user(AuthzConstants.EVERYONE).relation(permission)._object(part)
                                  .condition(new RelationshipCondition().name(DefaultAuthzModelGenerator.FOR_DEFINITION)
                                                                        .context(Map.of(DefaultAuthzModelGenerator.NAMED_DEFINITIONS, definitions)))));
            tuples.add(new TupleKey().user(part).relation(DefaultAuthzModelGenerator.PART)._object(role)
                                     .condition(new RelationshipCondition().name(DefaultAuthzModelGenerator.IN_PART)
                                                                           .context(Map.of(DefaultAuthzModelGenerator.PART_INDEX, index,
                                                                                           DefaultAuthzModelGenerator.PART_COUNT, count))));
            ret.add(tuples);
        });
        return ret;
    }

    // The hash a definition falls in a part of a role by, which is the same on every node and in every version
    private static long definitionHash(String definition) {
        CRC32 ret = new CRC32();
        ret.update(definition.getBytes(StandardCharsets.UTF_8));
        return ret.getValue();
    }

    @Override
    public Future<String> bind(String store, String roleId, String member, String object) {
        String binding = AuthzUtil.bindingObject(object, roleId);
        // A role has one binding on a resource, which every member granted the role there joins, so a check reads
        // one binding per role however many hold it. The binding is its role, its members and its attachment to
        // the object, and each grant writes all three, the role and the attachment being held already for every
        // grant but the first. The role is typed by its home and the binding by the object's type, so the engine
        // refuses a role the object's type may not grant
        List<RelationshipTuple> tuples = List.of(new RelationshipTuple(AuthzUtil.roleObject(roleId), AuthzConstants.ROLE_RELATION, binding),
                                                 new RelationshipTuple(member, AuthzConstants.MEMBER_RELATION, binding),
                                                 new RelationshipTuple(binding, AuthzConstants.ROLE_BINDING_RELATION, object));
        // under the lock leave takes, so a grant never joins a binding whose last member's leaving is detaching it
        return exclusively(binding, () -> ensure(store, tuples)).map(v -> AuthzUtil.grantId(roleId, member));
    }

    // Takes a member out of a binding, and the binding out of the store with its last member, so a role is
    // attached only where someone holds it
    private Future<Void> leave(String store, RelationshipTuple membership) {
        String binding = membership.object();
        return exclusively(binding, () -> remove(store, List.of(membership))
                .compose(v -> stores.storeIdOf(store))
                .compose(id -> fga.holdsAny(id, new ReadRequestTupleKey()._object(binding).relation(AuthzConstants.MEMBER_RELATION)))
                .compose(joined -> joined ? Future.succeededFuture() : removeBinding(store, binding)));
    }

    // Removes a binding's role, its members and its attachment to the object, whatever of them the store holds
    private Future<Void> unbind(String store, String binding, String object) {
        return read(store, binding).compose(held -> {
            List<RelationshipTuple> tuples = new ArrayList<>(held);
            tuples.add(new RelationshipTuple(binding, AuthzConstants.ROLE_BINDING_RELATION, object));
            return remove(store, tuples);
        });
    }

    @Override
    public <T> Future<T> exclusively(String object, Supplier<Future<T>> changes) {
        Validate.notBlank(object, "object cannot be blank");
        Validate.notNull(changes, "changes cannot be null");
        return vertx.sharedData().withLock(OBJECT_LOCK_PREFIX + object, OBJECT_LOCK_TIMEOUT_MILLIS, changes);
    }

    @Override
    public <T> Future<T> createExclusively(String object, Supplier<Future<T>> stored, Supplier<Future<Void>> clear, Supplier<Future<T>> create) {
        // the removal runs only when no record is stored, so it never takes the place of a record in use, and
        // under the lock a deletion takes, so a deletion's removal never takes the place this create writes
        return exclusively(object, () -> stored.get()
                .compose(existing -> existing == null
                        ? clear.get()
                        : Future.failedFuture(new AlreadyExistsException(object + " already exists")))
                .compose(v -> create.get()));
    }

    @Override
    public Future<Void> removeObject(String store, String object) {
        return read(store, object).compose(held -> {
            Future<Void> ret = Future.succeededFuture();
            List<RelationshipTuple> others = new ArrayList<>();
            for (RelationshipTuple tuple : held) {
                if (AuthzConstants.ROLE_BINDING_RELATION.equals(tuple.relation())) {
                    ret = ret.compose(v -> unbind(store, tuple.user(), object));
                } else {
                    others.add(tuple);
                    // a part is its role's alone, so it leaves the store with the role
                    if (DefaultAuthzModelGenerator.ROLE_PART.equals(AuthzUtil.typeOf(tuple.user()))) {
                        ret = ret.compose(v -> read(store, tuple.user())).compose(ofPart -> remove(store, ofPart));
                    }
                }
            }
            return ret.compose(v -> remove(store, others));
        });
    }

    @Override
    public Future<Void> removeSubject(String store, String user) {
        // the engine reads by user within one object type, so every type a version of the store's model has had is
        // read: a relationship written under a type the newest version dropped is still held, and holds again
        // once the type is declared again
        return stores.typesOf(store)
                     .compose(types -> requestEach(List.copyOf(types), type -> readByUser(store, user, type)))
                     .compose(reads -> {
                         Future<Void> ret = Future.succeededFuture();
                         List<RelationshipTuple> others = new ArrayList<>();
                         for (List<RelationshipTuple> held : reads) {
                             for (RelationshipTuple tuple : held) {
                                 if (AuthzUtil.typeOf(tuple.object()).endsWith(AuthzConstants.ROLE_BINDING_TYPE_SUFFIX)) {
                                     ret = ret.compose(v -> leave(store, tuple));
                                 } else {
                                     others.add(tuple);
                                 }
                             }
                         }
                         return ret.compose(v -> remove(store, others));
                     });
    }

    // Removes a binding wherever it is attached; the attachment names the binding as the user of a resource of the
    // type the binding's type is named after
    private Future<Void> removeBinding(String store, String binding) {
        String bindingType = AuthzUtil.typeOf(binding);
        String resourceType = bindingType.substring(0, bindingType.length() - AuthzConstants.ROLE_BINDING_TYPE_SUFFIX.length());
        return readByUser(store, binding, resourceType).compose(attachments -> {
            Future<Void> ret = Future.succeededFuture();
            for (RelationshipTuple attachment : attachments) {
                ret = ret.compose(v -> unbind(store, binding, attachment.object()));
            }
            return ret;
        });
    }

    @Override
    public Future<Void> revoke(String store, String grantId, String object) {
        // the binding is named by the object, so a grant made elsewhere is no member of it
        RelationshipTuple membership = new RelationshipTuple(AuthzUtil.grantMember(grantId), AuthzConstants.MEMBER_RELATION,
                                                             AuthzUtil.bindingObject(object, AuthzUtil.grantRole(grantId)));
        return holds(store, membership).compose(made -> {
            if (!made) {
                throw new IllegalArgumentException("No grant " + grantId + " was made on " + object);
            }
            return leave(store, membership);
        });
    }

    @Override
    public Future<List<Grant>> findGrants(String store, String object) {
        Resource resource = new Resource(AuthzUtil.typeOf(object), AuthzUtil.idOf(object));
        // the attachments alone: an organization, an application and a tenant each hold a tuple per member beside them
        return read(store, object, AuthzConstants.ROLE_BINDING_RELATION)
                .compose(attachments -> requestEach(attachments, attachment -> grantsOf(store, attachment.user(), resource)))
                .map(grants -> grants.stream().flatMap(List::stream).toList());
    }

    // One grant per member of the binding, of the binding's role; a member that is a userset no grant names is left out
    private Future<List<Grant>> grantsOf(String store, String binding, Resource resource) {
        return read(store, binding).map(held -> {
            String roleId = null;
            List<String> members = new ArrayList<>();
            for (RelationshipTuple tuple : held) {
                if (AuthzConstants.ROLE_RELATION.equals(tuple.relation())) {
                    roleId = AuthzUtil.idOf(tuple.user());
                } else if (AuthzConstants.MEMBER_RELATION.equals(tuple.relation()) && subjectOf(tuple.user()) != null) {
                    members.add(tuple.user());
                }
            }
            List<Grant> ret = new ArrayList<>();
            if (roleId != null) {
                for (String member : members) {
                    ret.add(new Grant(AuthzUtil.grantId(roleId, member), roleId, subjectOf(member), resource));
                }
            }
            return ret;
        });
    }

    private static Subject subjectOf(String user) {
        Subject ret = null;
        String type = AuthzUtil.typeOf(user);
        boolean members = user.endsWith("#" + AuthzConstants.MEMBER_RELATION);
        if (AuthzConstants.USER_TYPE.equals(type)) {
            ret = new Subject(SubjectType.USER, AuthzUtil.idOf(user));
        } else if (members && AuthzConstants.GROUP_TYPE.equals(type)) {
            ret = new Subject(SubjectType.GROUP, AuthzUtil.idOf(user));
        } else if (members && AuthzConstants.TENANT_TYPE.equals(type)) {
            ret = new Subject(SubjectType.TENANT, AuthzUtil.idOf(user));
        }
        return ret;
    }

    @Override
    public Future<AccessExplanation> explain(String store,
                                             AuthzModel model,
                                             RelationshipTuple holds,
                                             List<RelationshipTuple> context,
                                             List<Grant> grants) {
        // a role has a relation only for the permissions of its home and of the types inside it, and the engine
        // refuses a check for any other, so a grant of a role that cannot hold the permission is left out unasked
        List<Grant> candidates = grants.stream().filter(grant -> model.canHold(grant.roleId(), holds.relation())).toList();
        return stores.modelId(store).compose(modelId -> {
            // an administrator asks after changing access, so the answer must not predate the change
            Future<Boolean> allowed = check(store, modelId, checkRequest(holds, Consistency.HIGHER_CONSISTENCY, context, holds.object()), holds);
            return requestEach(candidates, grant -> explains(store, modelId, grant, holds))
                    .compose(explains -> allowed.map(held -> {
                        List<Grant> through = new ArrayList<>();
                        for (int i = 0; i < candidates.size(); i++) {
                            if (explains.get(i)) {
                                through.add(candidates.get(i));
                            }
                        }
                        return new AccessExplanation(held, through);
                    }));
        });
    }

    // Whether the grant gives the permission asked about to the user asked about: its role bundles the permission
    // or one implying it, and it was made to the user, or to a group or a tenant the user is a member of
    private Future<Boolean> explains(String store, String modelId, Grant grant, RelationshipTuple holds) {
        // the role's relation also holds every permission a bundled one implies, and one bundled for the definition
        // asked about, which only a check evaluates
        RelationshipTuple bundles = new RelationshipTuple(AuthzConstants.EVERYONE, holds.relation(), AuthzUtil.roleObject(grant.roleId()));
        Future<Boolean> bundled = check(store, modelId, checkRequest(bundles, Consistency.HIGHER_CONSISTENCY, List.of(), holds.object()), bundles);
        Future<Boolean> member;
        if (grant.subject().equals(subjectOf(holds.user()))) {
            member = Future.succeededFuture(true);
        } else if (grant.subject().type() != SubjectType.USER && AuthzConstants.USER_TYPE.equals(AuthzUtil.typeOf(holds.user()))) {
            String membersOf = grant.subject().type() == SubjectType.GROUP ? AuthzConstants.GROUP_TYPE : AuthzConstants.TENANT_TYPE;
            RelationshipTuple membership = new RelationshipTuple(holds.user(), AuthzConstants.MEMBER_RELATION,
                                                                 AuthzUtil.object(membersOf, grant.subject().id()));
            // an admin asks after changing a group or moving a user, so the answer must not predate the change
            member = check(store, modelId, checkRequest(membership, Consistency.HIGHER_CONSISTENCY, List.of(), membership.object()), membership);
        } else {
            member = Future.succeededFuture(false);
        }
        return Future.all(bundled, member).map(both -> both.<Boolean>resultAt(0) && both.<Boolean>resultAt(1));
    }

    @Override
    public Future<Boolean> check(String store, RelationshipTuple relationship, Consistency consistency, List<RelationshipTuple> context) {
        CheckRequest request = checkRequest(relationship, consistency, context, relationship.object());
        return stores.modelId(store).compose(modelId -> check(store, modelId, request, relationship));
    }

    /**
     * A check's request, for whichever version answers it.
     *
     * @param about the object the question is about, whose definition a role bundling a permission for the
     *              definitions it names is compared with
     */
    private static CheckRequest checkRequest(RelationshipTuple relationship, Consistency consistency, List<RelationshipTuple> context, String about) {
        CheckRequest ret = new CheckRequest().consistency(preference(consistency))
                                             .tupleKey(new CheckRequestTupleKey().user(relationship.user())
                                                                                 .relation(relationship.relation())
                                                                                 ._object(relationship.object()));
        if (!context.isEmpty()) {
            List<TupleKey> contextual = new ArrayList<>();
            for (RelationshipTuple tuple : context) {
                contextual.add(new TupleKey().user(tuple.user()).relation(tuple.relation())._object(tuple.object()));
            }
            ret.contextualTuples(new ContextualTupleKeys().tupleKeys(contextual));
        }
        // the engine refuses a check that reaches a tuple naming definitions without a definition to compare, so a
        // question about anything else names the empty one, which no tuple names
        String type = AuthzUtil.typeOf(about);
        String definition = "";
        if (AuthzConstants.ENTITY_DEFINITION_TYPE.equals(type)) {
            definition = AuthzUtil.idOf(about);
        } else if (AuthzConstants.TENANT_DEFINITION_TYPE.equals(type)) {
            definition = AuthzUtil.definitionOf(AuthzUtil.idOf(about));
        }
        return ret.context(Map.of(DefaultAuthzModelGenerator.CHECKED_DEFINITION, definition,
                                  DefaultAuthzModelGenerator.CHECKED_DEFINITION_HASH, definitionHash(definition)));
    }

    private Future<Boolean> check(String store, String modelId, CheckRequest request, RelationshipTuple asked) {
        return requestCheck(store, modelId, request)
                .recover(e -> onNewestModel(store, modelId, e, newest -> requestCheck(store, newest, request), false, asked));
    }

    // The engine refuses a request for what it names: a version of the store deleted and re-created under its name
    // since the caller read it, or a relation the version lacked when it was read and the newest has; the newest
    // version is read and answers once
    private <T> Future<T> onNewestModel(String store,
                                        String modelId,
                                        Throwable refusal,
                                        Function<String, Future<T>> request,
                                        T heldByNone,
                                        Object asked) {
        Future<T> ret;
        if (isValidationError(refusal)) {
            ret = stores.refreshModelId(store)
                        .compose(newest -> newest.equals(modelId) ? Future.failedFuture(refusal) : request.apply(newest))
                        // a relation or type the newest version lacks is held by no one: a permission of a type
                        // another store models, or of a service whose model the reconciler has yet to write
                        .recover(refused -> {
                            Future<T> held;
                            if (isValidationError(refused)) {
                                // a relation or type a caller named reaches here as it was sent, and comes back
                                // in the engine's refusal
                                log.warn("The model of store {} cannot answer {}: {}", store,
                                         KinoticUtil.singleLine(String.valueOf(asked)), KinoticUtil.singleLine(refused.getMessage()));
                                held = Future.succeededFuture(heldByNone);
                            } else {
                                held = Future.failedFuture(refused);
                            }
                            return held;
                        });
        } else {
            ret = Future.failedFuture(refusal);
        }
        return ret;
    }

    // A request the engine refused as invalid: the 400 it answers with for a version, type or relation the store
    // lacks. The SDK's FgaApiValidationError also covers the 422 of a request the engine throttled until it timed
    // out, which says nothing about the model
    private static boolean isValidationError(Throwable e) {
        return e instanceof ApiException api && api.getStatusCode() == 400;
    }

    private Future<Boolean> requestCheck(String store, String modelId, CheckRequest request) {
        request.authorizationModelId(modelId);
        return stores.storeIdOf(store).compose(id -> fga.check(id, request)).map(CheckResponse::getAllowed);
    }

    @Override
    public Future<List<String>> listObjects(String store, String user, String relation, String type, Consistency consistency) {
        return stores.modelId(store).compose(modelId -> requestListObjects(store, modelId, user, relation, type, preference(consistency))
                .recover(e -> onNewestModel(store, modelId, e,
                                            newest -> requestListObjects(store, newest, user, relation, type, preference(consistency)),
                                            List.of(), user + " " + relation + " " + type)));
    }

    private Future<List<String>> requestListObjects(String store, String modelId, String user, String relation, String type, ConsistencyPreference consistency) {
        ListObjectsRequest request = new ListObjectsRequest()
                .authorizationModelId(modelId)
                .consistency(consistency)
                .user(user)
                .relation(relation)
                .type(type);
        return stores.storeIdOf(store).compose(id -> fga.listObjects(id, request));
    }

    private static ConsistencyPreference preference(Consistency consistency) {
        return consistency == Consistency.HIGHER_CONSISTENCY
                ? ConsistencyPreference.HIGHER_CONSISTENCY
                : ConsistencyPreference.MINIMIZE_LATENCY;
    }

    // What the request of each item yields, in the items' order, with at most MAX_CONCURRENT_REQUESTS of the
    // requests under way at a time
    private static <T, R> Future<List<R>> requestEach(List<T> items, Function<T, Future<R>> request) {
        Future<List<R>> ret = Future.succeededFuture(new ArrayList<>());
        for (int from = 0; from < items.size(); from += MAX_CONCURRENT_REQUESTS) {
            List<T> batch = items.subList(from, Math.min(items.size(), from + MAX_CONCURRENT_REQUESTS));
            ret = ret.compose(collected -> Future.all(batch.stream().map(request).toList()).map(all -> {
                collected.addAll(all.list());
                return collected;
            }));
        }
        return ret;
    }

    private Future<List<RelationshipTuple>> read(String store, ReadRequestTupleKey key) {
        return stores.storeIdOf(store).compose(id -> fga.read(id, key)).map(tuples -> {
            List<RelationshipTuple> ret = new ArrayList<>();
            for (Tuple tuple : tuples) {
                ret.add(new RelationshipTuple(tuple.getKey().getUser(), tuple.getKey().getRelation(), tuple.getKey().getObject()));
            }
            return ret;
        });
    }
}
