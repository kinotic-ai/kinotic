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
import dev.openfga.sdk.api.model.ListObjectsRequest;
import dev.openfga.sdk.api.model.ListObjectsResponse;
import dev.openfga.sdk.api.model.ListStoresResponse;
import dev.openfga.sdk.api.model.ReadAuthorizationModelsResponse;
import dev.openfga.sdk.api.model.WriteAuthorizationModelRequest;
import dev.openfga.sdk.api.model.WriteAuthorizationModelResponse;
import dev.openfga.sdk.api.model.WriteRequest;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import io.vertx.core.Future;
import org.kinotic.authz.api.config.AuthzProperties;
import org.kinotic.authz.api.config.KinoticAuthzProperties;
import org.kinotic.core.api.utils.KinoticUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The OpenFGA client as the authorization services call it: the engine named by {@link AuthzProperties}, and
 * each call yielding its response's data as a Vert.x future on the caller's context, a failure to build or
 * send the request observed on the future like any other.
 */
@Component
public class OpenFgaService {

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

    public Future<ReadAuthorizationModelsResponse> readAuthorizationModels(String storeId, int pageSize, String continuationToken) {
        return call(() -> api.readAuthorizationModels(storeId, pageSize, continuationToken));
    }

    public Future<WriteAuthorizationModelResponse> writeAuthorizationModel(String storeId, WriteAuthorizationModelRequest request) {
        return call(() -> api.writeAuthorizationModel(storeId, request));
    }

    public Future<Void> write(String storeId, WriteRequest request) {
        return call(() -> api.write(storeId, request)).mapEmpty();
    }

    public Future<CheckResponse> check(String storeId, CheckRequest request) {
        return call(() -> api.check(storeId, request));
    }

    public Future<ListObjectsResponse> listObjects(String storeId, ListObjectsRequest request) {
        return call(() -> api.listObjects(storeId, request));
    }

    private static <T> Future<T> call(FgaCall<T> call) {
        Future<T> ret;
        try {
            ret = KinoticUtil.toFuture(call.start()).map(ApiResponse::getData);
        } catch (Exception e) {
            ret = Future.failedFuture(e);
        }
        return ret;
    }

}
