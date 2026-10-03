package org.kinotic.authz.api.model;

import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Set;

/**
 * A generated authorization model.
 *
 * @param definition  the model as OpenFGA's JSON authorization model, schema 1.1
 * @param hash        a digest of the definition, equal for equal definitions, so a store's model is rewritten
 *                    only when the definition changed
 * @param permissions the short permission names of every resource type in the model, keyed by type: the
 *                    catalog a role may bundle from
 */
public record AuthzModel(ObjectNode definition, String hash, Map<String, Set<String>> permissions) {
}
