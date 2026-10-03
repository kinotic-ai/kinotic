package org.kinotic.authz.internal.api.services;

import dev.openfga.sdk.api.model.CheckRequest;
import dev.openfga.sdk.api.model.CheckRequestTupleKey;
import dev.openfga.sdk.api.model.CheckResponse;
import dev.openfga.sdk.api.model.ListObjectsRequest;
import dev.openfga.sdk.api.model.ListObjectsResponse;
import dev.openfga.sdk.api.model.TupleKey;
import dev.openfga.sdk.api.model.TupleKeyWithoutCondition;
import dev.openfga.sdk.api.model.WriteRequest;
import dev.openfga.sdk.api.model.WriteRequestDeletes;
import dev.openfga.sdk.api.model.WriteRequestWrites;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.services.RelationshipService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultRelationshipService implements RelationshipService {

    // the most tuples one Write request may carry
    static final int WRITE_BATCH_SIZE = 100;

    private final OpenFgaService fga;

    @Override
    public Future<Void> write(String storeId, List<RelationshipTuple> writes, List<RelationshipTuple> deletes) {
        Future<Void> ret = Future.succeededFuture();
        for (WriteRequest request : requests(writes, deletes)) {
            ret = ret.compose(v -> fga.write(storeId, request));
        }
        return ret;
    }

    @Override
    public Future<Boolean> check(String storeId, String modelId, RelationshipTuple relationship) {
        CheckRequest request = new CheckRequest()
                .authorizationModelId(modelId)
                .tupleKey(new CheckRequestTupleKey()
                                  .user(relationship.user())
                                  .relation(relationship.relation())
                                  ._object(relationship.object()));
        return fga.check(storeId, request).map(CheckResponse::getAllowed);
    }

    @Override
    public Future<List<String>> listObjects(String storeId, String modelId, String user, String relation, String type) {
        ListObjectsRequest request = new ListObjectsRequest()
                .authorizationModelId(modelId)
                .user(user)
                .relation(relation)
                .type(type);
        return fga.listObjects(storeId, request).map(ListObjectsResponse::getObjects);
    }

    /**
     * The requests the given tuples take, each within the engine's limit, the writes before the deletes.
     */
    private static List<WriteRequest> requests(List<RelationshipTuple> writes, List<RelationshipTuple> deletes) {
        List<WriteRequest> ret = new ArrayList<>();
        for (int from = 0; from < writes.size(); from += WRITE_BATCH_SIZE) {
            List<TupleKey> keys = new ArrayList<>();
            for (RelationshipTuple tuple : writes.subList(from, Math.min(writes.size(), from + WRITE_BATCH_SIZE))) {
                keys.add(new TupleKey().user(tuple.user()).relation(tuple.relation())._object(tuple.object()));
            }
            ret.add(new WriteRequest().writes(new WriteRequestWrites().tupleKeys(keys)));
        }
        for (int from = 0; from < deletes.size(); from += WRITE_BATCH_SIZE) {
            List<TupleKeyWithoutCondition> keys = new ArrayList<>();
            for (RelationshipTuple tuple : deletes.subList(from, Math.min(deletes.size(), from + WRITE_BATCH_SIZE))) {
                keys.add(new TupleKeyWithoutCondition().user(tuple.user()).relation(tuple.relation())._object(tuple.object()));
            }
            ret.add(new WriteRequest().deletes(new WriteRequestDeletes().tupleKeys(keys)));
        }
        return ret;
    }

}
