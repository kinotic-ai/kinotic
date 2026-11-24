package org.kinotic.authz.internal.api.services;

/**
 * The version a store ran when it was read, kept by {@link DefaultAuthzStoreService} for the checks made
 * while it is current.
 *
 * @param id     the model id
 * @param readAt when it was read, in milliseconds since the epoch
 */
record ModelVersion(String id, long readAt) {
}
