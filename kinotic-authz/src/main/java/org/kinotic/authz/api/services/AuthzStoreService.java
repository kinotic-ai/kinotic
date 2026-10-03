package org.kinotic.authz.api.services;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AuthzModel;

/**
 * The stores of the authorization engine and the model each one runs.
 */
public interface AuthzStoreService {

    /**
     * The id of the platform store, resolved once per node: the configured id when the environment pins one,
     * else the store named {@code kinotic-platform}, created on the first start when there is none.
     *
     * @return the store id
     */
    Future<String> platformStoreId();

    /**
     * Creates a store. Store names are not unique in OpenFGA, so the caller records the id it gets back.
     *
     * @param name the store's name
     * @return the new store's id
     */
    Future<String> createStore(String name);

    /**
     * Makes the given model the store's current one. The store's latest version is kept when it already matches
     * the model, since versions are immutable and never deleted; otherwise the model is written as a new
     * version.
     *
     * @param storeId the store
     * @param model   the model the store must run
     * @return the id of the version the store now runs, the one every check names
     */
    Future<String> ensureModel(String storeId, AuthzModel model);

}
