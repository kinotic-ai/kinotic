package org.kinotic.authz.internal.api.services;

import dev.openfga.sdk.api.OpenFgaApi;
import dev.openfga.sdk.api.model.AuthorizationModel;
import dev.openfga.sdk.api.model.CreateStoreRequest;
import dev.openfga.sdk.api.model.CreateStoreResponse;
import dev.openfga.sdk.api.model.ListStoresResponse;
import dev.openfga.sdk.api.model.Store;
import dev.openfga.sdk.api.model.WriteAuthorizationModelRequest;
import dev.openfga.sdk.api.model.WriteAuthorizationModelResponse;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.config.KinoticAuthzProperties;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAuthzStoreService implements AuthzStoreService, SmartInitializingSingleton {

    static final String PLATFORM_STORE_NAME = "kinotic-platform";
    private static final int STORE_PAGE_SIZE = 100;
    // how long a starting node waits for each page of the platform store lookup
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);
    // the SDK's model classes bind by their own wire names, so the application's mapper customizations stay
    // out of the conversion in both directions
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final OpenFgaApi api;
    private final KinoticAuthzProperties properties;
    private String platformStoreId;

    @Override
    public void afterSingletonsInstantiated() {
        // resolved before the context finishes starting, so a node whose engine or store is unreachable never
        // comes up serving requests it cannot authorize
        try {
            platformStoreId = findPlatformStore();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while resolving the platform authorization store", e);
        } catch (Exception e) {
            throw new IllegalStateException("The platform authorization store could not be resolved from "
                                                    + properties.getAuthz().getApiUrl(), e);
        }
        log.info("Using platform authorization store {}", platformStoreId);
    }

    @Override
    public String platformStoreId() {
        return platformStoreId;
    }

    @Override
    public Future<String> createStore(String name) {
        return FgaCalls.data(() -> api.createStore(new CreateStoreRequest().name(name)))
                       .map(CreateStoreResponse::getId);
    }

    @Override
    public Future<String> ensureModel(String storeId, AuthzModel model) {
        return latestModel(storeId).compose(latest -> {
            Future<String> ret;
            if (latest != null && ModelHash.of(definitionOf(latest)).equals(model.hash())) {
                ret = Future.succeededFuture(latest.getId());
            } else {
                // nodes starting together may each write this version; versions are immutable, so the copies
                // are equal and the next comparison matches whichever is newest
                WriteAuthorizationModelRequest request = MAPPER.treeToValue(model.definition(),
                                                                            WriteAuthorizationModelRequest.class);
                ret = FgaCalls.data(() -> api.writeAuthorizationModel(storeId, request))
                              .map(WriteAuthorizationModelResponse::getAuthorizationModelId)
                              .onSuccess(id -> log.info("Wrote authorization model {} to store {}", id, storeId));
            }
            return ret;
        });
    }

    /**
     * The id of the one store named {@link #PLATFORM_STORE_NAME}, read page by page; fails when there is none
     * or several.
     */
    private String findPlatformStore() throws Exception {
        List<String> ids = new ArrayList<>();
        String continuationToken = null;
        do {
            ListStoresResponse page = api.listStores(STORE_PAGE_SIZE, continuationToken, PLATFORM_STORE_NAME)
                                         .get(STARTUP_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                                         .getData();
            for (Store store : page.getStores()) {
                // an engine older than the name filter returns every store, so the name is matched here too
                if (PLATFORM_STORE_NAME.equals(store.getName())) {
                    ids.add(store.getId());
                }
            }
            continuationToken = page.getContinuationToken();
        } while (continuationToken != null && !continuationToken.isEmpty());
        if (ids.isEmpty()) {
            throw new IllegalStateException("No store is named '" + PLATFORM_STORE_NAME
                                                    + "'; the platform store is created before the servers start");
        }
        if (ids.size() > 1) {
            // names are not unique in OpenFGA, so a second store of the name is a deployment mistake to fix
            throw new IllegalStateException(ids.size() + " stores are named '" + PLATFORM_STORE_NAME + "'; exactly one is expected");
        }
        return ids.getFirst();
    }

    /**
     * The store's newest model version, or null for a store with none.
     */
    private Future<AuthorizationModel> latestModel(String storeId) {
        // versions are listed newest first, so a page of one is the current version
        return FgaCalls.data(() -> api.readAuthorizationModels(storeId, 1, null))
                       .map(response -> response.getAuthorizationModels().isEmpty()
                               ? null : response.getAuthorizationModels().getFirst());
    }

    private static ObjectNode definitionOf(AuthorizationModel model) {
        ObjectNode ret = MAPPER.valueToTree(model);
        ret.remove("id");
        return ret;
    }

}
