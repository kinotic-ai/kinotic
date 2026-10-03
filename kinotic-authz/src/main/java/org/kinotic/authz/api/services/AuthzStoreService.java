package org.kinotic.authz.api.services;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AuthzModel;

/**
 * The stores of the authorization engine and the model each one runs.
 */
public interface AuthzStoreService {

    /**
     * The id of the platform store: the one store named {@code kinotic-platform}, resolved when the node starts.
     * The store is created ahead of the servers, never by one, and a node that finds no store of the name, or
     * more than one, does not start.
     *
     * @return the store id
     */
    String platformStoreId();

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
