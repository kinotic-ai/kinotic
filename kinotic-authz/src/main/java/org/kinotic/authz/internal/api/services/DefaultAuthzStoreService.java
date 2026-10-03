package org.kinotic.authz.internal.api.services;

import dev.openfga.sdk.api.OpenFgaApi;
import dev.openfga.sdk.api.model.AuthorizationModel;
import dev.openfga.sdk.api.model.CreateStoreRequest;
import dev.openfga.sdk.api.model.CreateStoreResponse;
import dev.openfga.sdk.api.model.Store;
import dev.openfga.sdk.api.model.WriteAuthorizationModelRequest;
import dev.openfga.sdk.api.model.WriteAuthorizationModelResponse;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.config.KinoticAuthzProperties;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAuthzStoreService implements AuthzStoreService {

    static final String PLATFORM_STORE_NAME = "kinotic-platform";
    private static final int STORE_PAGE_SIZE = 100;
    // the SDK's model classes bind by their own wire names, so the application's mapper customizations stay
    // out of the conversion in both directions
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final OpenFgaApi api;
    private final KinoticAuthzProperties properties;
    // resolved once per node; a failed resolution is retried by the next caller
    private volatile Future<String> platformStore;

    @Override
    public Future<String> platformStoreId() {
        Future<String> ret = platformStore;
        if (ret == null || ret.failed()) {
            synchronized (this) {
                ret = platformStore;
                if (ret == null || ret.failed()) {
                    ret = resolvePlatformStore();
                    platformStore = ret;
                }
            }
        }
        return ret;
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

    private Future<String> resolvePlatformStore() {
        String configured = properties.getAuthz().getPlatformStoreId();
        Future<String> ret;
        if (StringUtils.hasText(configured)) {
            ret = Future.succeededFuture(configured);
        } else {
            ret = findStore(PLATFORM_STORE_NAME).compose(found -> found != null
                    ? Future.succeededFuture(found)
                    : createStoreOnce(PLATFORM_STORE_NAME));
        }
        return ret.onSuccess(id -> log.info("Using platform authorization store {}", id));
    }

    /**
     * Creates the named store unless another node created it first. Store names are not unique in OpenFGA and
     * nodes start together, so after creating, the lowest id of that name is the one every node settles on,
     * the earliest created since store ids are ULIDs, and a node whose store lost removes it again.
     */
    private Future<String> createStoreOnce(String name) {
        return createStore(name).compose(created -> findStore(name).compose(winner -> {
            Future<String> ret;
            if (created.equals(winner)) {
                ret = Future.succeededFuture(created);
            } else {
                ret = FgaCalls.data(() -> api.deleteStore(created)).map(winner);
            }
            return ret;
        }));
    }

    /**
     * The lowest id among the stores of the given name, or null when there is none.
     */
    private Future<String> findStore(String name) {
        return collectStoreIds(name, null, new ArrayList<>())
                .map(ids -> ids.stream().min(String::compareTo).orElse(null));
    }

    private Future<List<String>> collectStoreIds(String name, String continuationToken, List<String> ids) {
        return FgaCalls.data(() -> api.listStores(STORE_PAGE_SIZE, continuationToken, name)).compose(page -> {
            for (Store store : page.getStores()) {
                // an engine older than the name filter returns every store, so the name is matched here too
                if (name.equals(store.getName())) {
                    ids.add(store.getId());
                }
            }
            Future<List<String>> ret;
            if (StringUtils.hasText(page.getContinuationToken())) {
                ret = collectStoreIds(name, page.getContinuationToken(), ids);
            } else {
                ret = Future.succeededFuture(ids);
            }
            return ret;
        });
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
