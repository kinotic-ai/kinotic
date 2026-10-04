package org.kinotic.authz.internal.api.services;

import dev.openfga.sdk.api.OpenFgaApi;
import dev.openfga.sdk.api.client.ApiResponse;
import dev.openfga.sdk.api.configuration.ApiToken;
import dev.openfga.sdk.api.configuration.Configuration;
import dev.openfga.sdk.api.configuration.Credentials;
import dev.openfga.sdk.api.model.CheckRequest;
import dev.openfga.sdk.api.model.CheckResponse;
import dev.openfga.sdk.api.model.CreateStoreRequest;
import dev.openfga.sdk.api.model.CreateStoreResponse;
import dev.openfga.sdk.api.model.GetStoreResponse;
import dev.openfga.sdk.api.model.ListObjectsRequest;
import dev.openfga.sdk.api.model.ListObjectsResponse;
import dev.openfga.sdk.api.model.ListStoresResponse;
import dev.openfga.sdk.api.model.ReadAuthorizationModelsResponse;
import dev.openfga.sdk.api.model.ReadRequest;
import dev.openfga.sdk.api.model.ReadRequestTupleKey;
import dev.openfga.sdk.api.model.Tuple;
import dev.openfga.sdk.api.model.TupleKey;
import dev.openfga.sdk.api.model.TupleKeyWithoutCondition;
import dev.openfga.sdk.api.model.WriteAuthorizationModelRequest;
import dev.openfga.sdk.api.model.WriteAuthorizationModelResponse;
import dev.openfga.sdk.api.model.WriteRequest;
import dev.openfga.sdk.api.model.WriteRequestDeletes;
import dev.openfga.sdk.api.model.WriteRequestWrites;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import io.vertx.core.Future;
import org.kinotic.authz.api.config.AuthzProperties;
import org.kinotic.authz.api.config.KinoticAuthzProperties;
import org.kinotic.core.api.utils.KinoticUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * The OpenFGA client as the authorization services call it: the engine named by {@link AuthzProperties}, and
 * each call yielding its response's data as a Vert.x future on the caller's context, a failure to build or
 * send the request observed on the future like any other.
 */
@Component
public class OpenFgaService {

    // the most tuples one Write request may carry
    static final int WRITE_BATCH_SIZE = 100;
    // the most tuples one Read request returns
    private static final int READ_PAGE_SIZE = 100;

    private final OpenFgaApi api;

    public OpenFgaService(KinoticAuthzProperties properties) {
        AuthzProperties authz = properties.getAuthz();
        Configuration configuration = new Configuration().apiUrl(authz.getApiUrl());
        if (StringUtils.hasText(authz.getApiToken())) {
            configuration.credentials(new Credentials(new ApiToken(authz.getApiToken())));
        }
        try {
            this.api = new OpenFgaApi(configuration);
        } catch (FgaInvalidParameterException e) {
            throw new IllegalArgumentException("Invalid OpenFGA client configuration", e);
        }
    }

    public Future<CreateStoreResponse> createStore(CreateStoreRequest request) {
        return call(() -> api.createStore(request));
    }

    public Future<ListStoresResponse> listStores(int pageSize, String continuationToken, String name) {
        return call(() -> api.listStores(pageSize, continuationToken, name));
    }

    public Future<Void> deleteStore(String storeId) {
        return call(() -> api.deleteStore(storeId)).mapEmpty();
    }

    /**
     * The store of the id, failing with the engine's not-found error for one deleted: the one call the engine
     * refuses for a deleted store, whose tuples and models it keeps answering for.
     */
    public Future<GetStoreResponse> getStore(String storeId) {
        return call(() -> api.getStore(storeId));
    }

    public Future<ReadAuthorizationModelsResponse> readAuthorizationModels(String storeId, int pageSize, String continuationToken) {
        return call(() -> api.readAuthorizationModels(storeId, pageSize, continuationToken));
    }

    public Future<WriteAuthorizationModelResponse> writeAuthorizationModel(String storeId, WriteAuthorizationModelRequest request) {
        return call(() -> api.writeAuthorizationModel(storeId, request));
    }

    /**
     * Adds and removes relationships, in as many requests as the engine's limit per request takes, the writes
     * before the deletes.
     */
    public Future<Void> write(String storeId, List<TupleKey> writes, List<TupleKeyWithoutCondition> deletes) {
        Future<Void> ret = Future.succeededFuture();
        for (int from = 0; from < writes.size(); from += WRITE_BATCH_SIZE) {
            WriteRequest request = new WriteRequest().writes(new WriteRequestWrites()
                                                                     .tupleKeys(writes.subList(from, Math.min(writes.size(), from + WRITE_BATCH_SIZE))));
            ret = ret.compose(v -> call(() -> api.write(storeId, request)).mapEmpty());
        }
        for (int from = 0; from < deletes.size(); from += WRITE_BATCH_SIZE) {
            WriteRequest request = new WriteRequest().deletes(new WriteRequestDeletes()
                                                                      .tupleKeys(deletes.subList(from, Math.min(deletes.size(), from + WRITE_BATCH_SIZE))));
            ret = ret.compose(v -> call(() -> api.write(storeId, request)).mapEmpty());
        }
        return ret;
    }

    public Future<CheckResponse> check(String storeId, CheckRequest request) {
        return call(() -> api.check(storeId, request));
    }

    /**
     * Every tuple matching the key, however many pages the engine answers in: the tuples on an object, those of
     * one user on it, or the one tuple a full key names.
     */
    public Future<List<Tuple>> read(String storeId, ReadRequestTupleKey key) {
        return read(storeId, key, null, new ArrayList<>());
    }

    private Future<List<Tuple>> read(String storeId, ReadRequestTupleKey key, String continuationToken, List<Tuple> collected) {
        ReadRequest request = new ReadRequest().tupleKey(key).pageSize(READ_PAGE_SIZE).continuationToken(continuationToken);
        return call(() -> api.read(storeId, request)).compose(response -> {
            collected.addAll(response.getTuples());
            String next = response.getContinuationToken();
            return next == null || next.isEmpty()
                    ? Future.succeededFuture(collected)
                    : read(storeId, key, next, collected);
        });
    }

    public Future<ListObjectsResponse> listObjects(String storeId, ListObjectsRequest request) {
        return call(() -> api.listObjects(storeId, request));
    }

    private static <T> Future<T> call(Callable<CompletableFuture<ApiResponse<T>>> call) {
        Future<T> ret;
        try {
            ret = KinoticUtil.toFuture(call.call()).map(ApiResponse::getData);
        } catch (Exception e) {
            ret = Future.failedFuture(e);
        }
        return ret;
    }

}
