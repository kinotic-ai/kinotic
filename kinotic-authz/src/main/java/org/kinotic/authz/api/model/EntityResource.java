package org.kinotic.authz.api.model;

/**
 * An entity definition as the model generator sees it: a resource type whose objects are the definition's
 * rows.
 *
 * @param typeName the resource type, the entity definition's name as a lowercase identifier
 * @param scope    what the rows are contained in
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public record EntityResource(String typeName, EntityScope scope) {
}
