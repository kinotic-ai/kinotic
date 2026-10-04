package org.kinotic.authz.internal.api.services;

import dev.openfga.sdk.api.model.CheckRequest;
import dev.openfga.sdk.api.model.CheckRequestTupleKey;
import dev.openfga.sdk.api.model.CheckResponse;
import dev.openfga.sdk.api.model.ListObjectsRequest;
import dev.openfga.sdk.api.model.ListObjectsResponse;
import dev.openfga.sdk.api.model.ReadRequestTupleKey;
import dev.openfga.sdk.api.model.Tuple;
import dev.openfga.sdk.api.model.TupleKey;
import dev.openfga.sdk.api.model.TupleKeyWithoutCondition;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DefaultRelationshipService implements RelationshipService {

    private final OpenFgaService fga;
    private final DefaultAuthzStoreService stores;

    @Override
    public Future<Void> write(String store, List<RelationshipTuple> writes, List<RelationshipTuple> deletes) {
        List<TupleKey> writeKeys = new ArrayList<>();
        for (RelationshipTuple tuple : writes) {
            writeKeys.add(new TupleKey().user(tuple.user()).relation(tuple.relation())._object(tuple.object()));
        }
        List<TupleKeyWithoutCondition> deleteKeys = new ArrayList<>();
        for (RelationshipTuple tuple : deletes) {
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
    public Future<Boolean> holds(String store, RelationshipTuple relationship) {
        return read(store, new ReadRequestTupleKey().user(relationship.user())
                                                    .relation(relationship.relation())
                                                    ._object(relationship.object()))
                .map(tuples -> !tuples.isEmpty());
    }

    @Override
    public Future<Void> ensure(String store, List<RelationshipTuple> relationships) {
        return missing(store, relationships).compose(missing -> write(store, missing, List.of()));
    }

    @Override
    public Future<Void> remove(String store, List<RelationshipTuple> relationships) {
        return missing(store, relationships).compose(missing -> {
            List<RelationshipTuple> held = new ArrayList<>(relationships);
            held.removeAll(missing);
            return write(store, List.of(), held);
        });
    }

    @Override
    public Future<Void> ensureRoles(String store, Map<String, Set<String>> roles) {
        List<Future<List<RelationshipTuple>>> reads = new ArrayList<>();
        List<String> ids = new ArrayList<>(roles.keySet());
        for (String roleId : ids) {
            reads.add(read(store, AuthzUtil.object(AuthzUtil.ROLE_TYPE, roleId)));
        }
        return Future.all(reads).compose(held -> {
            List<RelationshipTuple> writes = new ArrayList<>();
            List<RelationshipTuple> deletes = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                String role = AuthzUtil.object(AuthzUtil.ROLE_TYPE, ids.get(i));
                Set<String> desired = roles.get(ids.get(i));
                Set<String> bundled = new HashSet<>();
                for (RelationshipTuple tuple : held.<List<RelationshipTuple>>resultAt(i)) {
                    // a role's permissions are the tuples it holds for everyone; a binding or anything else
                    // attached to the role object is not a permission and is left alone
                    if (AuthzUtil.EVERYONE.equals(tuple.user())) {
                        bundled.add(tuple.relation());
                        if (!desired.contains(tuple.relation())) {
                            deletes.add(tuple);
                        }
                    }
                }
                for (String permission : desired) {
                    if (!bundled.contains(permission)) {
                        writes.add(new RelationshipTuple(AuthzUtil.EVERYONE, permission, role));
                    }
                }
            }
            return write(store, writes, deletes);
        });
    }

    @Override
    public Future<String> bind(String store, String roleId, String member, String object) {
        String bindingId = UUID.randomUUID().toString();
        String binding = AuthzUtil.object(AuthzUtil.ROLE_BINDING_TYPE, bindingId);
        return write(store,
                     List.of(new RelationshipTuple(AuthzUtil.object(AuthzUtil.ROLE_TYPE, roleId), AuthzUtil.ROLE_RELATION, binding),
                             new RelationshipTuple(member, AuthzUtil.MEMBER_RELATION, binding),
                             new RelationshipTuple(binding, AuthzUtil.ROLE_BINDING_RELATION, object)),
                     List.of())
                .map(bindingId);
    }

    @Override
    public Future<Boolean> check(String store, String modelId, RelationshipTuple relationship) {
        CheckRequest request = new CheckRequest()
                .authorizationModelId(modelId)
                .tupleKey(new CheckRequestTupleKey()
                                  .user(relationship.user())
                                  .relation(relationship.relation())
                                  ._object(relationship.object()));
        return stores.storeIdOf(store).compose(id -> fga.check(id, request)).map(CheckResponse::getAllowed);
    }

    @Override
    public Future<List<String>> listObjects(String store, String modelId, String user, String relation, String type) {
        ListObjectsRequest request = new ListObjectsRequest()
                .authorizationModelId(modelId)
                .user(user)
                .relation(relation)
                .type(type);
        return stores.storeIdOf(store).compose(id -> fga.listObjects(id, request)).map(ListObjectsResponse::getObjects);
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

    // The given relationships the store does not hold, in their order
    private Future<List<RelationshipTuple>> missing(String store, List<RelationshipTuple> relationships) {
        List<Future<Boolean>> held = new ArrayList<>();
        for (RelationshipTuple relationship : relationships) {
            held.add(holds(store, relationship));
        }
        return Future.all(held).map(results -> {
            List<RelationshipTuple> ret = new ArrayList<>();
            for (int i = 0; i < relationships.size(); i++) {
                if (!results.<Boolean>resultAt(i)) {
                    ret.add(relationships.get(i));
                }
            }
            return ret;
        });
    }
}
