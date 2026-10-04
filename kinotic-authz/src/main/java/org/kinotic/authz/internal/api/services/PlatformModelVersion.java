package org.kinotic.authz.internal.api.services;

/**
 * The version the platform store ran when it was read, kept by {@link DefaultAuthzStoreService} for the
 * checks made while it is current.
 *
 * @param id     the model id
 * @param readAt when it was read, in milliseconds since the epoch
 */
record PlatformModelVersion(String id, long readAt) {
}
