package org.kinotic.authz.internal.api.services;

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
import org.kinotic.core.api.utils.KinoticUtil;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.concurrent.CompletableFuture;


@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAuthzStoreService implements AuthzStoreService {

    static final String PLATFORM_STORE_NAME = "kinotic-platform";
    // the SDK's model classes bind by their own wire names, so the application's mapper customizations stay
    // out of the conversion in both directions
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final OpenFgaService fga;
    private final KinoticAuthzProperties properties;
    // kept once found, as a stage with no context of its own; a failed lookup is replaced by the next caller's
    private volatile CompletableFuture<String> platformStore;

    @Override
    public Future<String> ensurePlatformModel(AuthzModel model) {
        return platformStoreId().compose(storeId -> ensureModel(storeId, model));
    }

    @Override
    public Future<String> platformModelId() {
        return platformStoreId().compose(this::latestModel).map(model -> {
            if (model == null) {
                throw new IllegalStateException("The platform store runs no model yet");
            }
            return model.getId();
        });
    }

    /**
     * The engine's id of the store named as its record is: the platform's through {@link #platformStoreId()};
     * any other name fails the caller.
     */
    Future<String> storeIdOf(String store) {
        Future<String> ret;
        if (PLATFORM.equals(store)) {
            ret = platformStoreId();
        } else {
            ret = Future.failedFuture(new IllegalArgumentException("No authorization store is named '" + store + "'"));
        }
        return ret;
    }

    /**
     * The id of the platform store, resolved once and kept; a failed lookup is made again by the next caller.
     */
    Future<String> platformStoreId() {
        CompletableFuture<String> stage = platformStore;
        if (stage == null || stage.isCompletedExceptionally()) {
            synchronized (this) {
                stage = platformStore;
                if (stage == null || stage.isCompletedExceptionally()) {
                    stage = findPlatformStore().toCompletionStage().toCompletableFuture();
                    platformStore = stage;
                }
            }
        }
        // a Vert.x future dispatches its listeners onto the context it was created on, so the one future handed
        // to every caller would run each caller's continuation on the first caller's context; each caller bridges
        // the stage onto its own instead
        return KinoticUtil.toFuture(stage);
    }

    @Override
    public Future<String> createStore(String name) {
        return fga.createStore(new CreateStoreRequest().name(name)).map(CreateStoreResponse::getId);
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
                ret = fga.writeAuthorizationModel(storeId, request)
                         .map(WriteAuthorizationModelResponse::getAuthorizationModelId)
                              .onSuccess(id -> log.info("Wrote authorization model {} to store {}", id, storeId));
            }
            return ret;
        });
    }

    /**
     * The id of the store named {@link #PLATFORM_STORE_NAME}; fails when there is none.
     */
    private Future<String> findPlatformStore() {
        return fga.listStores(1, null, PLATFORM_STORE_NAME).compose(page -> {
            Future<String> ret;
            Store store = page.getStores().isEmpty() ? null : page.getStores().getFirst();
            if (store != null && PLATFORM_STORE_NAME.equals(store.getName())) {
                ret = Future.succeededFuture(store.getId());
            } else {
                ret = Future.failedFuture(new IllegalStateException("No store is named '" + PLATFORM_STORE_NAME
                                                                            + "'; the platform store is created before the servers start"));
            }
            return ret;
        }).onSuccess(id -> log.info("Using platform authorization store {}", id))
          .onFailure(e -> log.warn("The platform authorization store could not be resolved from {}: {}",
                                   properties.getAuthz().getApiUrl(), e.getMessage()));
    }

    /**
     * The store's newest model version, or null for a store with none.
     */
    private Future<AuthorizationModel> latestModel(String storeId) {
        // versions are listed newest first, so a page of one is the current version
        return fga.readAuthorizationModels(storeId, 1, null)
                  .map(response -> response.getAuthorizationModels().isEmpty()
                               ? null : response.getAuthorizationModels().getFirst());
    }

    private static ObjectNode definitionOf(AuthorizationModel model) {
        ObjectNode ret = MAPPER.valueToTree(model);
        ret.remove("id");
        return ret;
    }

}
