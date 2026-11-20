package org.kinotic.authz.api.model;

import java.util.Map;
import java.util.Set;

/**
 * A role as the console edits it: a name, a description and the permissions it bundles. A built-in role is
 * defined by the model, named after its type and level, and cannot be changed; a custom role is defined by an
 * organization, for itself or for one of its applications, from the permissions the model has. Every role is homed at a resource type, bundles only
 * permissions of that type and of the types inside it, and is granted only on that type or on one above it within
 * the same tier.
 *
 * @param id           the role's id, {@code <resourceType>.<name>}; null for a custom role not saved yet
 * @param name         the role's name
 * @param description  what the role is for, or null
 * @param builtIn      true for a role the model defines
 * @param resourceType the type the role is homed at, such as {@code project} for {@code project.editor}
 * @param grantableOn  the resource types of the caller's tier the role may be granted on; ignored when saving
 * @param permissions  the model names of the permissions the role bundles on everything a grant of it reaches, such
 *                     as {@code project_can_edit}
 * @param definitionPermissions for a role of an application, the permissions it bundles for single entity
 *                     definitions: each definition's id to the model names of the definition's permissions held on
 *                     it alone, such as {@code entity_definition_can_read}; empty for every other role
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public record RoleDefinition(String id,
                             String name,
                             String description,
                             boolean builtIn,
                             String resourceType,
                             Set<String> grantableOn,
                             Set<String> permissions,
                             Map<String, Set<String>> definitionPermissions) {
}
