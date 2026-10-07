package org.kinotic.idl.api.schema.decorators;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import java.util.List;

/** Carries a compiled permission contract for a service function or entity operation. */
@Getter
@Setter
@Accessors(chain = true)
public final class RequirePermissionC3Decorator extends C3Decorator {
    public static final String type = "RequirePermission";
    private String permission;
    private String resourceType;
    private String idArgument;
    private int argumentIndex = -1;
    private String label;
    private boolean tenantDelegable;

    public RequirePermissionC3Decorator() {
        super.type = type;
        this.targets = List.of(DecoratorTarget.TYPE, DecoratorTarget.FUNCTION, DecoratorTarget.FIELD);
    }
}
