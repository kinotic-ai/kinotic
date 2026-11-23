package org.kinotic.authz.internal.api.services;

import java.util.Set;

/**
 * The types of every model version a store has run, as {@link DefaultAuthzStoreService} read them, kept while
 * the store's newest version is the one they were read up to.
 *
 * @param newestModelId the id of the store's newest version when the types were read
 * @param types         the type names, each once
 */
record KnownTypes(String newestModelId, Set<String> types) {
}
