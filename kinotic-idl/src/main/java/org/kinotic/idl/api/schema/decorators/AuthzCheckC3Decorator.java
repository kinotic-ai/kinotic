package org.kinotic.idl.api.schema.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * The authorization check a function requires, resolved from its declaration and parameters: which object
 * is checked, which permission, and where in a request the object's identity is found. The gateway resolves
 * the templates against each request, and the model generator reads the permission into the model.
 */
@Getter
@Setter
@Accessors(chain = true)
public final class AuthzCheckC3Decorator extends C3Decorator {

    @JsonIgnore
    public static final String type = "AuthzCheck";

    /**
     * The type of the object the check is made on: a literal such as {@code application}, or a template such
     * as {@code {entityDefinitionId}} when the type is an argument of the call.
     */
    private String resource;

    /**
     * The id of the object the check is made on, as a template over the function's parameters and the
     * caller's scope, such as {@code {projectId}}, {@code {registration.id}} or {@code {@organizationId}}.
     */
    private String objectId;

    /**
     * The type the permission is named for: the service's own type for a check on one of its resources, and
     * also the service's own type for a check made on the parent, where the permission reads as
     * "{@code <type>_<permission>} within this parent". A literal or a template like {@link #resource}.
     */
    private String permissionResource;

    /**
     * The short permission name, such as {@code can_edit}; its model name is
     * {@code <permissionResource>_<permission>}.
     */
    private String permission;

    /**
     * Short names of permissions on {@link #permissionResource} that this permission implies.
     */
    private List<String> implies = List.of();

    /**
     * True for a function declared zone-only, which any caller the zone admits may call: the derivation drops
     * the check of such a function, so a stored definition carries none.
     */
    private boolean zoneOnly;

    /**
     * Whether the check must answer from the stored relationships rather than the engine's caches.
     */
    private boolean consistent;

    public AuthzCheckC3Decorator() {
        this.targets = List.of(DecoratorTarget.FUNCTION);
    }
}
