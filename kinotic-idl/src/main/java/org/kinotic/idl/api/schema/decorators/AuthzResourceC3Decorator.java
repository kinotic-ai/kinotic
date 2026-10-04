package org.kinotic.idl.api.schema.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Marks a service as acting on one authorization resource type: the service's functions are checked against
 * that type, and the type, with its containment parent, is part of the generated authorization model.
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
     * The object every function of the service is checked on, as a template over the caller's scope, unless the
     * function declares its own; null when each function's object is derived from what it names.
     */
    private String objectId;

    public AuthzResourceC3Decorator() {
        this.targets = List.of(DecoratorTarget.TYPE);
    }
}
