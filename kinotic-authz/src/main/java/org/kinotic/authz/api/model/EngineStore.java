package org.kinotic.authz.api.model;

/**
 * The engine's ids of one store: the store's own, and the model version it runs.
 *
 * @param storeId the engine's id of the store
 * @param modelId the id of the model version the store runs, the one every check against it names
 */
public record EngineStore(String storeId, String modelId) {
}
