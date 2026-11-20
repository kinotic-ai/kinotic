package org.kinotic.authz.api.model;

import java.util.Map;
import java.util.Set;

/**
 * The permissions a role bundles: those its holders have on everything a grant of it reaches, and, for a role of an
 * application, the permissions of single entity definitions its holders have on those definitions alone.
 *
 * @param permissions           the model names of the permissions held on every resource a grant of the role reaches,
 *                              such as {@code project_can_edit}
 * @param definitionPermissions for each entity definition the role names, by the definition's id, the model names of
 *                              the definition's permissions held on it alone, such as
 *                              {@code entity_definition_can_read}
 */
public record RolePermissions(Set<String> permissions, Map<String, Set<String>> definitionPermissions) {

    /**
     * The permissions of a role that names no definition.
     *
     * @param permissions the model names of the permissions held on every resource a grant of the role reaches
     */
    public RolePermissions(Set<String> permissions) {
        this(permissions, Map.of());
    }

}
