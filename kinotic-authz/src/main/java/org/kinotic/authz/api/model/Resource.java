package org.kinotic.authz.api.model;

/**
 * A resource in a store, the node a grant is made on or a check is made against.
 *
 * @param type the resource type, as its service declares it: {@code organization}, {@code application},
 *             {@code project}, {@code entity_definition}
 * @param id   the resource's id
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public record Resource(String type, String id) {
}
