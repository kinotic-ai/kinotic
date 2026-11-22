package org.kinotic.authz.internal.api.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import org.kinotic.authz.api.model.AuthzNameUtil;
import org.kinotic.authz.api.model.Consistency;
import org.kinotic.authz.api.model.EngineStore;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.model.RolePermissions;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.authz.api.model.SubjectType;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    // the locks the objects of one type share
    private static final int OBJECT_LOCK_STRIPES = 512;
    // the most requests one operation has under way at the engine; the client opens a connection for each
    private static final int MAX_CONCURRENT_REQUESTS = 16;
    // The engine stores up to 32,768 bytes of condition context in a tuple. The definitions one permission names
    // are spread over parts of a third of that on average, since hashing fills the parts unevenly.
    private static final int PART_CONTEXT_BYTES = 11_000;
    // what the engine counts for a definition named in a tuple, beside the bytes of its id
    private static final int NAMED_DEFINITION_BYTES = 8;

    private static final long HELD_ROLES_CAPACITY = 100_000;
    private static final Duration HELD_ROLES_RETENTION = Duration.ofMinutes(5);

    private final OpenFgaService fga;
    private final DefaultAuthzStoreService stores;
    private final Vertx vertx;
    // The ids of the roles a user holds on anything in a store, as last listed, by heldRolesKey. A check names them
    // so the engine reads the bindings of those roles alone. A role missing from a kept list is found by the
    // listing a refusal is followed by, and one the user no longer holds names a binding the user is no member of
    private final Cache<String, Set<String>> heldRoles = Caffeine.newBuilder()
                                                                 .maximumSize(HELD_ROLES_CAPACITY)
                                                                 .expireAfterWrite(HELD_ROLES_RETENTION)
                                                                 .build();

    // Adds and removes relationships, in as many requests as the engine's limit per request takes
    private Future<Void> write(String store, List<RelationshipTuple> writes, List<RelationshipTuple> deletes) {
        // the engine refuses a request that names a tuple twice, as tuples gathered from several records can, such
        // as the tenant every user of it places under the application
        List<TupleKey> writeKeys = new ArrayList<>();
        for (RelationshipTuple tuple : new LinkedHashSet<>(writes)) {
            TupleKey key = new TupleKey().user(tuple.user()).relation(tuple.relation())._object(tuple.object());
            // a resource holds a binding under the role the binding grants, which a check compares with the roles
            // its caller holds
            if (AuthzConstants.ROLE_BINDING_RELATION.equals(tuple.relation())) {
                key.condition(new RelationshipCondition().name(DefaultAuthzModelGenerator.ROLE_HELD)
                                                         .context(Map.of(DefaultAuthzModelGenerator.BOUND_ROLE, AuthzNameUtil.bindingRole(tuple.user()))));
            }
            writeKeys.add(key);
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
    public Future<RolePermissions> findRolePermissions(String store, String roleId) {
        return stores.storeIdOf(store).compose(id -> readRole(id, roleObject(store, roleId))).map(HeldRole::permissions);
    }

    // A role's object, typed by its home. An application's store has a role type for each type it places, so a role
    // homed at a type of the application's own services is held there as a role of the tenant, the level a
    // permission of such a service is checked on
    private static String roleObject(String store, String roleId) {
        return AuthzStoreService.places(store, AuthzNameUtil.roleHome(roleId))
                ? AuthzNameUtil.roleObject(roleId)
                : AuthzNameUtil.object(AuthzNameUtil.roleType(AuthzConstants.TENANT_TYPE), roleId);
    }

    // Every relation of a role is a permission it bundles for everything, but the one it holds its parts by; every
    // relation of a part is a permission the role bundles for what the part's tuple names: definitions, or under
    // SERVICE_PERMISSION the permissions of the application's services
    private Future<HeldRole> readRole(String storeId, String role) {
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
                Set<String> ofServices = new TreeSet<>();
                List<TupleKeyWithoutCondition> partTuples = new ArrayList<>();
                for (int i = 0; i < parts.size(); i++) {
                    partTuples.add(new TupleKeyWithoutCondition().user(parts.get(i)).relation(DefaultAuthzModelGenerator.PART)._object(role));
                    for (Tuple tuple : ofParts.get(i)) {
                        String permission = tuple.getKey().getRelation();
                        partTuples.add(new TupleKeyWithoutCondition().user(tuple.getKey().getUser()).relation(permission)._object(parts.get(i)));
                        Object definitions = ((Map<?, ?>) tuple.getKey().getCondition().getContext()).get(DefaultAuthzModelGenerator.NAMED_DEFINITIONS);
                        for (Object definition : ((Map<?, ?>) definitions).keySet()) {
                            if (DefaultAuthzModelGenerator.SERVICE_PERMISSION.equals(permission)) {
                                ofServices.add(String.valueOf(definition));
                            } else {
                                named.computeIfAbsent(String.valueOf(definition), k -> new TreeSet<>()).add(permission);
                            }
                        }
                    }
                }
                return new HeldRole(new RolePermissions(everywhere, named, ofServices), partTuples);
            });
        });
    }

    @Override
    public Future<List<String>> findBindings(String store, String user, Set<String> boundOn) {
        // a binding is typed by what it is attached to, so it is read on each type
        return requestEach(List.copyOf(boundOn), type -> readByUser(store, user, AuthzNameUtil.roleBindingType(type)))
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
        String parentType = parents.get(AuthzNameUtil.typeOf(object));
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
    public Future<Void> ensureRole(String store, String roleId, RolePermissions permissions) {
        return converging(() -> reconcileRoles(store, Map.of(roleId, permissions)));
    }

    @Override
    public Future<EngineStore> ensureModelWithRoles(String store, AuthzModel model) {
        // A check that runs while a role's tuples are being written can cache the role's answer from before the
        // write, valid for the engine's cache lifetime; the roles are written while no version yet grants through
        // their new relations, so no check against the model can read them before they are in step
        Map<String, RolePermissions> bundled = new HashMap<>();
        model.storedRoles().forEach((roleId, permissions) -> bundled.put(roleId, model.bundle(permissions)));
        return stores.ensureRoleRelations(store, model)
                     .compose(v -> converging(() -> reconcileRoles(store, bundled)))
                     .compose(v -> stores.ensureModel(store, model))
                     .compose(modelId -> stores.storeIdOf(store).map(storeId -> new EngineStore(storeId, modelId)));
    }

    private Future<Void> reconcileRoles(String store, Map<String, RolePermissions> roles) {
        List<String> ids = new ArrayList<>(roles.keySet());
        return stores.storeIdOf(store).compose(storeId -> requestEach(ids, roleId -> readRole(storeId, roleObject(store, roleId))).compose(held -> {
            List<TupleKey> writes = new ArrayList<>();
            List<TupleKeyWithoutCondition> deletes = new ArrayList<>();
            List<List<TupleKey>> parts = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                String role = roleObject(store, ids.get(i));
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
                Map<String, Set<String>> named = named(desired);
                // a name falls in a part by the number of parts, so a change to what a role names is every part
                // deleted and written again
                if (!named.equals(named(bundled))) {
                    deletes.addAll(held.get(i).partTuples());
                    parts.addAll(partsOf(role, named));
                }
            }
            // every delete is made before the writes, so a role is never read holding what it held and what it is
            // to hold together; a part is written whole in a request of its own, which keeps each request small
            return fga.write(storeId, List.of(), deletes)
                      .compose(v -> fga.write(storeId, writes, List.of()))
                      .compose(v -> requestEach(parts, part -> fga.write(storeId, part, List.of())))
                      .mapEmpty();
        }));
    }

    // What a role holds on the things it names and does not hold for everything: a definition's permissions on the
    // definition, and a permission of the application's services, by its name, under SERVICE_PERMISSION
    private static Map<String, Set<String>> named(RolePermissions permissions) {
        Map<String, Set<String>> ret = new TreeMap<>();
        permissions.definitionPermissions().forEach((definition, held) -> ret.computeIfAbsent(definition, k -> new TreeSet<>()).addAll(held));
        permissions.servicePermissions().forEach(permission -> ret.computeIfAbsent(permission, k -> new TreeSet<>())
                                                                  .add(DefaultAuthzModelGenerator.SERVICE_PERMISSION));
        ret.values().forEach(alone -> alone.removeAll(permissions.permissions()));
        ret.values().removeIf(Set::isEmpty);
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
            String part = AuthzNameUtil.object(DefaultAuthzModelGenerator.ROLE_PART, AuthzNameUtil.idOf(role) + "/" + index);
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
        String binding = AuthzNameUtil.bindingObject(object, roleId);
        // A role has one binding on a resource, which every member granted the role there joins, so a check reads
        // one binding per role however many hold it. The binding is its role, its members, its attachment to the
        // object and its place among the role's bindings, which the roles a user holds are listed through. Each
        // grant writes all four, every one but the member being held already for every grant but the first. The
        // role is typed by its home and the binding by the object's type, so the engine refuses a role the
        // object's type may not grant
        List<RelationshipTuple> tuples = List.of(new RelationshipTuple(roleObject(store, roleId), AuthzConstants.ROLE_RELATION, binding),
                                                 new RelationshipTuple(member, AuthzConstants.MEMBER_RELATION, binding),
                                                 new RelationshipTuple(binding, AuthzConstants.ROLE_BINDING_RELATION, object),
                                                 heldThrough(binding));
        // under the lock leave takes, so a grant never joins a binding whose last member's leaving is detaching it
        return exclusively(binding, () -> ensure(store, tuples)).map(v -> AuthzNameUtil.grantId(roleId, member));
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

    // Removes a binding's role, its members, its attachment to the object and its place among the role's bindings,
    // whatever of them the store holds
    private Future<Void> unbind(String store, String binding, String object) {
        return read(store, binding).compose(held -> {
            List<RelationshipTuple> tuples = new ArrayList<>(held);
            tuples.add(new RelationshipTuple(binding, AuthzConstants.ROLE_BINDING_RELATION, object));
            tuples.add(heldThrough(binding));
            return remove(store, tuples);
        });
    }

    // The binding's place among the bindings of its role, whose members hold the role
    private static RelationshipTuple heldThrough(String binding) {
        return new RelationshipTuple(binding, DefaultAuthzModelGenerator.BINDING,
                                     AuthzNameUtil.object(DefaultAuthzModelGenerator.ROLE_HOLDERS, AuthzNameUtil.bindingRole(binding)));
    }

    @Override
    public <T> Future<T> exclusively(String object, Supplier<Future<T>> changes) {
        Validate.notBlank(object, "object cannot be blank");
        Validate.notNull(changes, "changes cannot be null");
        return vertx.sharedData().withLock(lockOf(object), OBJECT_LOCK_TIMEOUT_MILLIS, changes);
    }

    // An object takes one of a fixed number of locks of its type, chosen by the hash of its id, which every node
    // computes alike. The cluster keeps every lock name it is asked for, about 300 bytes each in a region of 100 MB
    // that all nodes share, so the names are bounded. Objects sharing a lock wait on each other as callers changing
    // one object do. A type's locks are its own, so a change holding one can take the lock of an object of another
    // type
    private static String lockOf(String object) {
        return OBJECT_LOCK_PREFIX + AuthzNameUtil.typeOf(object) + "." + Math.floorMod(AuthzNameUtil.idOf(object).hashCode(), OBJECT_LOCK_STRIPES);
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
                    if (DefaultAuthzModelGenerator.ROLE_PART.equals(AuthzNameUtil.typeOf(tuple.user()))) {
                        ret = ret.compose(v -> read(store, tuple.user())).compose(ofPart -> remove(store, ofPart));
                    }
                }
            }
            return ret.compose(v -> remove(store, others));
        });
    }

    @Override
    public Future<Void> removeObjectsOf(String store, String user, String type) {
        return readByUser(store, user, type).compose(held -> {
            Future<Void> ret = Future.succeededFuture();
            for (String object : held.stream().map(RelationshipTuple::object).distinct().toList()) {
                ret = ret.compose(v -> removeObject(store, object));
            }
            return ret;
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
                                 if (AuthzNameUtil.typeOf(tuple.object()).endsWith(AuthzConstants.ROLE_BINDING_TYPE_SUFFIX)) {
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
        String bindingType = AuthzNameUtil.typeOf(binding);
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
        RelationshipTuple membership = new RelationshipTuple(AuthzNameUtil.grantMember(grantId), AuthzConstants.MEMBER_RELATION,
                                                             AuthzNameUtil.bindingObject(object, AuthzNameUtil.grantRole(grantId)));
        return holds(store, membership).compose(made -> {
            if (!made) {
                throw new IllegalArgumentException("No grant " + grantId + " was made on " + object);
            }
            return leave(store, membership);
        });
    }

    @Override
    public Future<List<Grant>> findGrants(String store, String object) {
        Resource resource = new Resource(AuthzNameUtil.typeOf(object), AuthzNameUtil.idOf(object));
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
                    roleId = AuthzNameUtil.idOf(tuple.user());
                } else if (AuthzConstants.MEMBER_RELATION.equals(tuple.relation()) && AuthzNameUtil.subjectOf(tuple.user()) != null) {
                    members.add(tuple.user());
                }
            }
            List<Grant> ret = new ArrayList<>();
            if (roleId != null) {
                for (String member : members) {
                    ret.add(new Grant(AuthzNameUtil.grantId(roleId, member), roleId, AuthzNameUtil.subjectOf(member), resource));
                }
            }
            return ret;
        });
    }

    // What a user holds when it is one of those a grant to a group, a tenant or an application stands for
    private static RelationshipTuple oneOf(String user, Subject subject) {
        return new RelationshipTuple(user, subject.type().getRelation(), AuthzNameUtil.object(subject.type().getObjectType(), subject.id()));
    }

    @Override
    public Future<AccessExplanation> explain(String store,
                                             AuthzModel model,
                                             RelationshipTuple holds,
                                             List<RelationshipTuple> context,
                                             List<Grant> grants) {
        // a permission of an application's own services is asked as the relation standing for them all, by its name
        boolean ofService = model.servicePermissions().containsKey(holds.relation());
        RelationshipTuple asked = ofService
                ? new RelationshipTuple(holds.user(), DefaultAuthzModelGenerator.SERVICE_PERMISSION, holds.object())
                : holds;
        String named = ofService ? holds.relation() : definitionOf(holds.object());
        List<RelationshipTuple> held = new ArrayList<>(context);
        held.addAll(holdingByDeclaration(store, model.rolesHolding(holds.relation())));
        // a role has a relation only for the permissions of its home and of the types inside it, and the engine
        // refuses a check for any other, so a grant of a role that cannot hold the permission is left out unasked
        List<Grant> candidates = grants.stream()
                                       .filter(grant -> model.hasRelation(AuthzNameUtil.typeOf(roleObject(store, grant.roleId())), asked.relation()))
                                       .toList();
        return stores.modelId(store).compose(modelId -> {
            // an administrator asks after changing access, so the answer must not predate the change
            Future<Boolean> allowed = check(store, modelId, asked, Consistency.HIGHER_CONSISTENCY, held, named);
            return requestEach(candidates, grant -> explains(store, modelId, grant, asked, held, named))
                    .compose(explains -> allowed.map(holding -> {
                        List<Grant> through = new ArrayList<>();
                        for (int i = 0; i < candidates.size(); i++) {
                            if (explains.get(i)) {
                                through.add(candidates.get(i));
                            }
                        }
                        return new AccessExplanation(holding, through);
                    }));
        });
    }

    // Whether the grant gives the permission asked about to the user asked about: its role bundles the permission
    // or one implying it, and it was made to the user, or to a group or a tenant the user is a member of, or to
    // the application the user is an end user of
    private Future<Boolean> explains(String store,
                                     String modelId,
                                     Grant grant,
                                     RelationshipTuple holds,
                                     List<RelationshipTuple> context,
                                     String named) {
        // the role's relation also holds every permission a bundled one implies, and one bundled for what the
        // question names, which only a check evaluates
        RelationshipTuple bundles = new RelationshipTuple(AuthzConstants.EVERYONE, holds.relation(), roleObject(store, grant.roleId()));
        Future<Boolean> bundled = check(store, modelId, bundles, Consistency.HIGHER_CONSISTENCY, context, named);
        Future<Boolean> member;
        if (grant.subject().equals(AuthzNameUtil.subjectOf(holds.user()))) {
            member = Future.succeededFuture(true);
        } else if (grant.subject().type() != SubjectType.USER && AuthzConstants.USER_TYPE.equals(AuthzNameUtil.typeOf(holds.user()))) {
            // an admin asks after changing a group or moving a user, so the answer must not predate the change
            member = check(store, modelId, oneOf(holds.user(), grant.subject()), Consistency.HIGHER_CONSISTENCY, List.of(), "");
        } else {
            member = Future.succeededFuture(false);
        }
        return Future.all(bundled, member).map(both -> both.<Boolean>resultAt(0) && both.<Boolean>resultAt(1));
    }

    @Override
    public Future<Boolean> check(String store, RelationshipTuple relationship, Consistency consistency, List<RelationshipTuple> context) {
        return stores.modelId(store).compose(modelId -> check(store, modelId, relationship, consistency, context, definitionOf(relationship.object())));
    }

    @Override
    public Future<Boolean> checkServicePermission(String store,
                                                  String user,
                                                  String permission,
                                                  String object,
                                                  Set<String> roles,
                                                  Consistency consistency) {
        RelationshipTuple relationship = new RelationshipTuple(user, DefaultAuthzModelGenerator.SERVICE_PERMISSION, object);
        List<RelationshipTuple> held = holdingByDeclaration(store, roles);
        return stores.modelId(store).compose(modelId -> check(store, modelId, relationship, consistency, held, permission));
    }

    // A built-in role of a type of an application's own services holds its permissions by the contracts declaring
    // them and by no stored tuple, so a check of one such permission takes each role holding it as holding every
    // permission of the services, for this check alone
    private static List<RelationshipTuple> holdingByDeclaration(String store, Set<String> roles) {
        List<RelationshipTuple> ret = new ArrayList<>();
        for (String roleId : roles) {
            ret.add(new RelationshipTuple(AuthzConstants.EVERYONE, DefaultAuthzModelGenerator.SERVICE_PERMISSION, roleObject(store, roleId)));
        }
        return ret;
    }

    // The definition a question about an object is asked for: the object when it is one, the one of a definition
    // within a tenant, and the empty one, which no role names, for any other object
    private static String definitionOf(String about) {
        String type = AuthzNameUtil.typeOf(about);
        String ret = "";
        if (AuthzConstants.ENTITY_DEFINITION_TYPE.equals(type)) {
            ret = AuthzNameUtil.idOf(about);
        } else if (AuthzConstants.TENANT_DEFINITION_TYPE.equals(type)) {
            ret = AuthzNameUtil.definitionOf(AuthzNameUtil.idOf(about));
        }
        return ret;
    }

    /**
     * A check's request, for whichever version answers it.
     *
     * @param named what the question is asked for, a definition or a permission of the application's services,
     *              which a role bundling a permission for what its parts name is compared with; the engine refuses
     *              a check that reaches such a part with nothing to compare, so a question about anything else
     *              names the empty string
     * @param held  the ids of the roles the user asked about holds in the store, whose bindings the engine reads
     */
    private static CheckRequest checkRequest(RelationshipTuple relationship,
                                             Consistency consistency,
                                             List<RelationshipTuple> context,
                                             String named,
                                             Set<String> held) {
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
        return ret.context(conditionContext(named, held));
    }

    // What the model's conditions compare a tuple with: the definition or service permission asked for, with its
    // hash, and the roles the user asked about holds
    private static Map<String, Object> conditionContext(String named, Set<String> held) {
        Map<String, Boolean> byRole = new TreeMap<>();
        held.forEach(roleId -> byRole.put(roleId, true));
        return Map.of(DefaultAuthzModelGenerator.CHECKED_DEFINITION, named,
                      DefaultAuthzModelGenerator.CHECKED_DEFINITION_HASH, definitionHash(named),
                      DefaultAuthzModelGenerator.HELD_ROLES, byRole);
    }

    // Asks with the roles the user is known to hold, listed when none are kept. A kept list may predate a grant, so
    // a refusal it led to is followed by a listing, and asked again when the user holds other roles than were kept
    private Future<Boolean> check(String store,
                                  String modelId,
                                  RelationshipTuple asked,
                                  Consistency consistency,
                                  List<RelationshipTuple> context,
                                  String named) {
        Set<String> kept = heldRoles.getIfPresent(heldRolesKey(store, asked.user()));
        Future<Set<String>> held = kept != null ? Future.succeededFuture(kept) : listHeldRoles(store, modelId, asked.user());
        return held.compose(roles -> check(store, modelId, checkRequest(asked, consistency, context, named, roles), asked))
                   .compose(allowed -> {
                       Future<Boolean> ret;
                       if (allowed || kept == null) {
                           ret = Future.succeededFuture(allowed);
                       } else {
                           ret = listHeldRoles(store, modelId, asked.user()).compose(roles -> roles.equals(kept)
                                   ? Future.succeededFuture(false)
                                   : check(store, modelId, checkRequest(asked, consistency, context, named, roles), asked));
                       }
                       return ret;
                   });
    }

    private Future<Boolean> check(String store, String modelId, CheckRequest request, RelationshipTuple asked) {
        return requestCheck(store, modelId, request)
                .recover(e -> onNewestModel(store, modelId, e, newest -> requestCheck(store, newest, request), false, asked));
    }

    private static String heldRolesKey(String store, String user) {
        return store + "|" + user;
    }

    // Lists the roles the user holds on anything in the store, directly or as a member of what a role was granted
    // to, and keeps the list. Everyone together is granted no role, so is listed none
    private Future<Set<String>> listHeldRoles(String store, String modelId, String user) {
        Future<Set<String>> ret;
        if (AuthzConstants.EVERYONE.equals(user)) {
            ret = Future.succeededFuture(Set.of());
        } else {
            // the list a refusal is followed by must hold a grant made a moment before
            ret = listObjects(store, modelId, user, DefaultAuthzModelGenerator.HOLDER, DefaultAuthzModelGenerator.ROLE_HOLDERS,
                              ConsistencyPreference.HIGHER_CONSISTENCY, Set.of())
                    .map(objects -> {
                        Set<String> roles = new TreeSet<>();
                        objects.forEach(object -> roles.add(AuthzNameUtil.idOf(object)));
                        heldRoles.put(heldRolesKey(store, user), roles);
                        return roles;
                    });
        }
        return ret;
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
        // a listing has no refusal to follow with a second one, so it names the roles the user holds as they are now
        return stores.modelId(store).compose(modelId -> listHeldRoles(store, modelId, user)
                .compose(held -> listObjects(store, modelId, user, relation, type, preference(consistency), held)));
    }

    private Future<List<String>> listObjects(String store,
                                             String modelId,
                                             String user,
                                             String relation,
                                             String type,
                                             ConsistencyPreference consistency,
                                             Set<String> held) {
        return requestListObjects(store, modelId, user, relation, type, consistency, held)
                .recover(e -> onNewestModel(store, modelId, e,
                                            newest -> requestListObjects(store, newest, user, relation, type, consistency, held),
                                            List.of(), user + " " + relation + " " + type));
    }

    private Future<List<String>> requestListObjects(String store,
                                                    String modelId,
                                                    String user,
                                                    String relation,
                                                    String type,
                                                    ConsistencyPreference consistency,
                                                    Set<String> held) {
        ListObjectsRequest request = new ListObjectsRequest()
                .authorizationModelId(modelId)
                .consistency(consistency)
                .user(user)
                .relation(relation)
                .type(type)
                .context(conditionContext("", held));
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

    private Future<List<RelationshipTuple>> read(String store, String object) {
        return read(store, new ReadRequestTupleKey()._object(object));
    }

    private Future<List<RelationshipTuple>> read(String store, String object, String relation) {
        return read(store, new ReadRequestTupleKey()._object(object).relation(relation));
    }

    private Future<List<RelationshipTuple>> readByUser(String store, String user, String objectType) {
        // the engine reads by user within one object type, named by the type alone
        return read(store, new ReadRequestTupleKey().user(user)._object(objectType + ":"));
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
