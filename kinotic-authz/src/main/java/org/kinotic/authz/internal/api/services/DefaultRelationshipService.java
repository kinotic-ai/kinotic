package org.kinotic.authz.internal.api.services;

import dev.openfga.sdk.api.model.ReadRequestTupleKey;
import dev.openfga.sdk.api.model.RelationshipCondition;
import dev.openfga.sdk.api.model.Tuple;
import dev.openfga.sdk.api.model.TupleKey;
import dev.openfga.sdk.api.model.TupleKeyWithoutCondition;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.authz.api.model.AuthzNameUtil;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.services.ObjectLockService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DefaultRelationshipService implements RelationshipService {

    private final OpenFgaService fga;
    private final DefaultAuthzStoreService stores;
    private final ObjectLockService locks;

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
    public Future<List<String>> findBindings(String store, String user, Set<String> boundOn) {
        // a binding is typed by what it is attached to, so it is read on each type
        return OpenFgaService.requestEach(List.copyOf(boundOn), type -> readByUser(store, user, AuthzNameUtil.roleBindingType(type)))
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
        return OpenFgaService.converging(() -> write(store, relationships, List.of()));
    }

    @Override
    public Future<Void> remove(String store, List<RelationshipTuple> relationships) {
        return OpenFgaService.converging(() -> write(store, List.of(), relationships));
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
        List<RelationshipTuple> tuples = List.of(new RelationshipTuple(DefaultRolePermissionsService.roleObject(store, roleId), AuthzConstants.ROLE_RELATION, binding),
                                                 new RelationshipTuple(member, AuthzConstants.MEMBER_RELATION, binding),
                                                 new RelationshipTuple(binding, AuthzConstants.ROLE_BINDING_RELATION, object),
                                                 heldThrough(binding));
        // under the lock leave takes, so a grant never joins a binding whose last member's leaving is detaching it
        return locks.exclusively(binding, () -> ensure(store, tuples)).map(v -> AuthzNameUtil.grantId(roleId, member));
    }

    // Takes a member out of a binding, and the binding out of the store with its last member, so a role is
    // attached only where someone holds it
    private Future<Void> leave(String store, RelationshipTuple membership) {
        String binding = membership.object();
        return locks.exclusively(binding, () -> remove(store, List.of(membership))
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
                     .compose(types -> OpenFgaService.requestEach(List.copyOf(types), type -> readByUser(store, user, type)))
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
                .compose(attachments -> OpenFgaService.requestEach(attachments, attachment -> grantsOf(store, attachment.user(), resource)))
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
