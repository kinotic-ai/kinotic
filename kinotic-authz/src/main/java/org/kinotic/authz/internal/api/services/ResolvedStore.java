package org.kinotic.authz.internal.api.services;

/**
 * A store's engine id as a node resolved it, and when, so the node can tell when to confirm it with the engine.
 *
 * @param id         the engine's id of the store
 * @param resolvedAt when the id was resolved or last confirmed, in epoch milliseconds
 */
record ResolvedStore(String id, long resolvedAt) {
}
