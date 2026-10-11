package org.kinotic.authz.api.services;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AuthzModel;

/**
 * The stores of the authorization engine and the model each one runs. Every operation names its store the
 * way the store's record is named, {@link #PLATFORM} for the platform's and an application's id for its own,
 * and the engine's own id is resolved from the name.
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public interface AuthzStoreService {

    /**
     * The name of the platform's store, the id of its record. An application's store is named by the
     * application's id.
     */
    String PLATFORM = "platform";

    /**
     * Makes the store exist in the engine. The platform's store is created ahead of the servers and never by
     * one, so for it this is a lookup that fails when there is none; an application's store is created when
     * the engine has none of its name. A store found or created is kept, and a lookup that fails, because the
     * engine is unreachable, fails the caller and is made again by the next one.
     *
     * @param store the store, named as its record is
     * @return the engine's id of the store
     */
    Future<String> ensureStore(String store);

    /**
     * Deletes a store from the engine with every model and relationship it holds. A store the engine does not
     * have leaves nothing to delete.
     *
     * @param store the store, named as its record is
     * @return completes when the engine has no store of the name
     */
    Future<Void> deleteStore(String store);

    /**
     * The id of the version the store runs, read from the engine and kept for a while: the version every check
     * against the store names.
     *
     * @param store the store, named as its record is
     * @return the version's id; fails when the store does not exist or runs no model yet
     */
    Future<String> modelId(String store);

    /**
     * Makes the given model the store's current one. The store's latest version is kept when it already matches
     * the model, since versions are immutable and never deleted; otherwise the model is written as a new
     * version.
     *
     * @param store the store, named as its record is
     * @param model the model the store must run
     * @return the id of the version the store now runs, the one every check names
     */
    Future<String> ensureModel(String store, AuthzModel model);

}
