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
 * @param roles       the built-in roles the model implies, each role's id to the model names of the permissions it
 *                    bundles: a viewer, an editor and an admin of every type with permissions, the admin holding
 *                    the type's own and everything inside it, the roles the type's services declare, and for a
 *                    platform store the application developer and the platform operator and support
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public record AuthzModel(ObjectNode definition, String hash, Map<String, Set<String>> permissions, Map<String, Set<String>> roles) {
}
