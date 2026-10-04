package org.kinotic.idl.api.directory;

import org.kinotic.idl.api.schema.decorators.AuthzRoleDeclaration;

import java.util.List;

/**
 * What a service declares about the resource its functions act on, as {@code @AuthzResource} and its
 * TypeScript mirror declare it. A value left out is null.
 *
 * @param value      the resource type, a lowercase identifier such as {@code project}, or a template such as
 *                   {@code {entityDefinitionId}} for a type each request names
 * @param parent     the type the resource sits under, such as {@code application}, or null for a root type
 * @param objectId   the id template every function is checked on, when the service acts on one object
 * @param permission the permission every function of the service needs, when they all need the same one
 * @param roles      the roles the service declares for its type
 */
public record AuthzResourceDeclaration(String value,
                                       String parent,
                                       String objectId,
                                       String permission,
                                       List<AuthzRoleDeclaration> roles) {

    public AuthzResourceDeclaration {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
