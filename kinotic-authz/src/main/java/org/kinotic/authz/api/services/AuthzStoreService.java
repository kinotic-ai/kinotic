package org.kinotic.authz.api.services;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AuthzModel;

/**
 * The stores of the authorization engine and the model each one runs.
 */
public interface AuthzStoreService {

    /**
     * The name of the platform's store, the id of its record. An application's store is named by the
     * application's id.
     */
    String PLATFORM = "platform";

    /**
     * Makes the given model the platform store's current one, as {@link #ensureModel} does for a store named
     * by id. The platform store is the one store named {@code kinotic-platform}, created ahead of the servers
     * and never by one; it is looked up on first use and kept once found, and a lookup that fails, because the
     * engine is unreachable or there is no store of the name, fails the caller and is made again by the next
     * one.
     *
     * @param model the model the platform store must run
     * @return the id of the version the platform store now runs
     */
    Future<String> ensurePlatformModel(AuthzModel model);

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
