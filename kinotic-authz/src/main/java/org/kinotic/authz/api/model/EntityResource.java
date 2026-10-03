package org.kinotic.authz.api.model;

/**
 * An entity definition as the model generator sees it: a resource type whose objects are the definition's
 * rows.
 *
 * @param typeName the resource type, the entity definition's name as a lowercase identifier
 * @param scope    what the rows are contained in
 */
public record EntityResource(String typeName, EntityScope scope) {
}
