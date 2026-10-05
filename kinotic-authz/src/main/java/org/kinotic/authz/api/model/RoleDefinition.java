package org.kinotic.authz.api.model;

import java.util.Set;

/**
 * A role as the console edits it: a name, a description and the permissions it bundles. A built-in role is
 * defined by the model, named after its type and level, and cannot be changed; a custom role is defined by an
 * organization from the permissions the model has.
 *
 * @param id          the role's id, null for a custom role not saved yet
 * @param name        the role's name
 * @param description what the role is for, or null
 * @param builtIn     true for a role the model defines
 * @param permissions the model names of the permissions the role bundles, such as {@code project_can_edit}
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public record RoleDefinition(String id, String name, String description, boolean builtIn, Set<String> permissions) {
}
