package org.kinotic.authz.internal.api.services;

import dev.openfga.sdk.api.model.AuthorizationModel;
import dev.openfga.sdk.api.model.CreateStoreRequest;
import dev.openfga.sdk.api.model.CreateStoreResponse;
import dev.openfga.sdk.api.model.Store;
import dev.openfga.sdk.api.model.WriteAuthorizationModelRequest;
import dev.openfga.sdk.api.model.WriteAuthorizationModelResponse;
import dev.openfga.sdk.errors.FgaError;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.hashing.LongTupleHashFunction;
import org.kinotic.authz.api.config.KinoticAuthzProperties;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.core.api.utils.KinoticUtil;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAuthzStoreService implements AuthzStoreService, InitializingBean {

    static final String PLATFORM_STORE_NAME = "kinotic-platform";
    private static final String APPLICATION_STORE_PREFIX = "kinotic-app-";
    // the SDK's model classes bind by their own wire names, so the application's mapper customizations stay
    // out of the conversion in both directions
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    // how long a store's engine id and newest model version are kept before the engine is asked again
    static final long RETENTION_MILLIS = 30_000;
    private static final int STORE_PAGE_SIZE = 100;
    // the address every node hears a store's deletion on, so none keeps answering with the id of a store the
    // engine has deleted while another node already resolved the one created again under its name
    static final String STORE_DELETED_ADDRESS = "kinotic.authz.store-deleted";

    private final OpenFgaService fga;
    private final KinoticAuthzProperties properties;
    private final Vertx vertx;
    // each store's engine id, kept once found, as a stage with no context of its own; a failed lookup is
    // replaced by the next caller's, and a kept id is confirmed with the engine once the retention has passed
    private final Map<String, CompletableFuture<ResolvedStore>> storeIds = new ConcurrentHashMap<>();
    // the newest model version of each engine store, by the engine's id so a store re-created under a name
    // never answers with its predecessor's version
    private final Map<String, ModelVersion> modelVersions = new ConcurrentHashMap<>();

    @Override
    public Future<String> ensureStore(String store) {
        Future<String> ret;
        if (PLATFORM.equals(store)) {
            ret = storeIdOf(store);
        } else {
            // asked by name every time, so a store deleted and re-created under its name since this node last
            // resolved it is created again rather than answered with the id the engine no longer knows
            String name = storeNameOf(store);
            ret = findStore(name)
                    .compose(found -> found != null
                            ? Future.succeededFuture(found)
                            : fga.createStore(new CreateStoreRequest().name(name))
                                 .map(CreateStoreResponse::getId)
                                 .onSuccess(id -> log.info("Created authorization store {} for '{}'", id, store))
                                 .compose(id -> keepOldest(store, name, id)))
                    .onSuccess(id -> storeIds.put(store, CompletableFuture.completedFuture(new ResolvedStore(id, System.currentTimeMillis()))));
        }
        return ret;
    }

    @Override
    public void afterPropertiesSet() {
        vertx.eventBus().<JsonObject>consumer(STORE_DELETED_ADDRESS,
                                              message -> forgetIfKept(message.body().getString("store"), message.body().getString("id")));
    }

    @Override
    public Future<Void> deleteStore(String store) {
        return findStore(storeNameOf(store))
                .compose(id -> id == null ? Future.succeededFuture() : fga.deleteStore(id).onSuccess(v -> {
                    forget(store, id);
                    vertx.eventBus().publish(STORE_DELETED_ADDRESS, new JsonObject().put("store", store).put("id", id));
                }))
                .onSuccess(v -> log.info("Deleted the authorization store of '{}'", store));
    }

    // Another node deleted the store: its id is forgotten where it is the one kept, so the next caller resolves
    // the name again; a node that already resolved the store created again under the name keeps that one
    private void forgetIfKept(String store, String storeId) {
        storeIds.computeIfPresent(store, (name, stage) -> stage.isDone() && !stage.isCompletedExceptionally()
                && stage.join().id().equals(storeId) ? null : stage);
        modelVersions.remove(storeId);
    }

    @Override
    public Future<String> modelId(String store) {
        return storeIdOf(store).compose(storeId -> version(store, storeId, false));
    }

    /**
     * The store's newest model version read from the engine now, replacing the one kept, for a caller the kept
     * one answered with a validation error: the version of the store deleted and re-created under its name
     * since, or one without a relation the newest has.
     */
    Future<String> refreshModelId(String store) {
        return storeIdOf(store).compose(storeId -> version(store, storeId, true));
    }

    private Future<String> version(String store, String storeId, boolean refresh) {
        ModelVersion kept = refresh ? null : modelVersions.get(storeId);
        Future<String> ret;
        if (kept != null && kept.readAt() + RETENTION_MILLIS > System.currentTimeMillis()) {
            ret = Future.succeededFuture(kept.id());
        } else {
            // every request's check names the version, so it is read once and kept; the reconciler's write of a
            // new version reaches a node within the retention, or at once where a check it refuses asks again
            ret = latestModel(storeId).map(model -> {
                if (model == null) {
                    throw new IllegalStateException("The store of '" + store + "' runs no model yet");
                }
                modelVersions.put(storeId, new ModelVersion(model.getId(), System.currentTimeMillis()));
                return model.getId();
            });
        }
        return ret;
    }

    @Override
    public Future<String> ensureModel(String store, AuthzModel model) {
        return storeIdOf(store).compose(storeId -> latestModel(storeId).compose(latest -> {
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
        }));
    }

    /**
     * The engine's id of the store named as its record is, resolved once and kept. The engine keeps answering
     * for a deleted store's tuples and models, so a kept id is confirmed with it once the retention has passed,
     * and one of a store deleted since, as an application's is when the application is deleted and created
     * again under its id, is resolved again by name. A failed lookup is made again by the next caller.
     */
    Future<String> storeIdOf(String store) {
        CompletableFuture<ResolvedStore> stage = storeIds.get(store);
        if (stale(stage)) {
            synchronized (this) {
                stage = storeIds.get(store);
                if (stale(stage)) {
                    ResolvedStore kept = stage != null && stage.isDone() && !stage.isCompletedExceptionally() ? stage.join() : null;
                    stage = resolve(store, kept).toCompletionStage().toCompletableFuture();
                    storeIds.put(store, stage);
                }
            }
        }
        // a Vert.x future dispatches its listeners onto the context it was created on, so the one future handed
        // to every caller would run each caller's continuation on the first caller's context; each caller bridges
        // the stage onto its own instead
        return KinoticUtil.toFuture(stage).map(ResolvedStore::id);
    }

    private static boolean stale(CompletableFuture<ResolvedStore> stage) {
        return stage == null
                || stage.isCompletedExceptionally()
                || (stage.isDone() && stage.join().resolvedAt() + RETENTION_MILLIS < System.currentTimeMillis());
    }

    // A kept id the engine still has is kept; one it reports gone is forgotten, with the version read from it,
    // and the name resolved again
    private Future<ResolvedStore> resolve(String store, ResolvedStore kept) {
        Future<String> ret;
        if (kept == null) {
            ret = requireStore(store);
        } else {
            ret = fga.getStore(kept.id())
                     .map(found -> kept.id())
                     .recover(e -> {
                         Future<String> again;
                         if (e instanceof FgaError error && error.isNotFoundError()) {
                             forget(store, kept.id());
                             again = requireStore(store);
                         } else {
                             again = Future.failedFuture(e);
                         }
                         return again;
                     });
        }
        return ret.map(id -> new ResolvedStore(id, System.currentTimeMillis()));
    }

    private void forget(String store, String storeId) {
        storeIds.remove(store);
        modelVersions.remove(storeId);
    }

    // The engine's name of a store: the platform's fixed one, or an application's prefixed by what it is
    // The engine caps a store's name at 64 characters, which an application's id need not fit, so an application's
    // store is named by the id's 128-bit digest
    private static String storeNameOf(String store) {
        String ret;
        if (PLATFORM.equals(store)) {
            ret = PLATFORM_STORE_NAME;
        } else {
            long[] hash = LongTupleHashFunction.xx128().hashChars(store);
            ret = APPLICATION_STORE_PREFIX + HexFormat.of().formatHex(ByteBuffer.allocate(Long.BYTES * 2).putLong(hash[0]).putLong(hash[1]).array());
        }
        return ret;
    }

    private Future<String> requireStore(String store) {
        String name = storeNameOf(store);
        return findStore(name).map(id -> {
            if (id == null) {
                throw new IllegalStateException(PLATFORM.equals(store)
                        ? "No store is named '" + name + "'; the platform store is created before the servers start"
                        : "No store is named '" + name + "'; the application's store is created when its record is reconciled");
            }
            return id;
        }).onSuccess(id -> log.info("Using authorization store {} for '{}'", id, store))
          .onFailure(e -> log.warn("The authorization store of '{}' could not be resolved from {}: {}",
                                   store, properties.getAuthz().getApiUrl(), e.getMessage()));
    }

    // Nodes creating a store together, as the provisioner and the reconciler do for a new application, each
    // create one under the name; the oldest is the store of the name, so a creator whose store is not it deletes
    // its own and answers with the oldest, which every lookup by name answers with as well
    private Future<String> keepOldest(String store, String name, String created) {
        return findStore(name).compose(oldest -> oldest.equals(created)
                ? Future.succeededFuture(created)
                : fga.deleteStore(created)
                     .map(oldest)
                     .onSuccess(v -> log.info("Deleted authorization store {} of '{}', created beside {}", created, store, oldest)));
    }

    /**
     * The id of the store of the given name, the oldest where several carry it, or null when the engine has none.
     */
    private Future<String> findStore(String name) {
        return findStores(name, null, new ArrayList<>()).map(ids -> ids.stream().min(String::compareTo).orElse(null));
    }

    // the engine's ids are time-ordered, so the smallest is the oldest
    private Future<List<String>> findStores(String name, String continuationToken, List<String> collected) {
        return fga.listStores(STORE_PAGE_SIZE, continuationToken, name).compose(page -> {
            for (Store store : page.getStores()) {
                if (name.equals(store.getName())) {
                    collected.add(store.getId());
                }
            }
            String next = page.getContinuationToken();
            return next == null || next.isEmpty()
                    ? Future.succeededFuture(collected)
                    : findStores(name, next, collected);
        });
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
