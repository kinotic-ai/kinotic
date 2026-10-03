package org.kinotic.authz.internal.api.services;

import dev.openfga.sdk.api.model.CheckRequest;
import dev.openfga.sdk.api.model.CheckRequestTupleKey;
import dev.openfga.sdk.api.model.CheckResponse;
import dev.openfga.sdk.api.model.ListObjectsRequest;
import dev.openfga.sdk.api.model.ListObjectsResponse;
import dev.openfga.sdk.api.model.TupleKey;
import dev.openfga.sdk.api.model.TupleKeyWithoutCondition;
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

    private final OpenFgaService fga;

    @Override
    public Future<Void> write(String storeId, List<RelationshipTuple> writes, List<RelationshipTuple> deletes) {
        List<TupleKey> writeKeys = new ArrayList<>();
        for (RelationshipTuple tuple : writes) {
            writeKeys.add(new TupleKey().user(tuple.user()).relation(tuple.relation())._object(tuple.object()));
        }
        List<TupleKeyWithoutCondition> deleteKeys = new ArrayList<>();
        for (RelationshipTuple tuple : deletes) {
            deleteKeys.add(new TupleKeyWithoutCondition().user(tuple.user()).relation(tuple.relation())._object(tuple.object()));
        }
        return fga.write(storeId, writeKeys, deleteKeys);
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

}
