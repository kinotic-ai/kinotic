package org.kinotic.idl.api.schema.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Marks a service as acting on one authorization resource type: the service's functions are checked against
 * that type, and the type, with its containment parent and the roles it declares, is part of the generated
 * authorization model.
 */
@Getter
@Setter
@Accessors(chain = true)
public final class AuthzResourceC3Decorator extends C3Decorator {

    @JsonIgnore
    public static final String type = "AuthzResource";

    /**
     * The resource type the service acts on, such as {@code project}.
     */
    private String resourceType;

    /**
     * The type that contains this one and from which grants inherit, or null for a root type.
     */
    private String parent;

    /**
     * The resource every function of the service is checked on, as a template over the caller's scope, unless the
     * function declares its own; null when each function's resource is derived from what it names.
     */
    private String resourceId;

    /**
     * The permission every function of the service requires unless it declares its own; null when each
     * function's permission is derived from its name.
     */
    private String permission;

    /**
     * The roles the service declares for its type beside the built-in ones; empty when it declares none.
     */
    private List<AuthzRoleDeclaration> roles = List.of();

    public AuthzResourceC3Decorator() {
        this.targets = List.of(DecoratorTarget.TYPE);
    }
}
