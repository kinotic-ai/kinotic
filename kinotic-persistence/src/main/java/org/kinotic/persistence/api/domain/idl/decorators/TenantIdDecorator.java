package org.kinotic.persistence.api.domain.idl.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.kinotic.idl.api.schema.decorators.C3Decorator;
import org.kinotic.idl.api.schema.decorators.DecoratorTarget;

import java.util.List;

/**
 * Denotes the field that will hold the tenant id to use as the Multi Tenant discriminator field
 * Created By Navíd Mitchell 🤪on 2/12/25
 */
public final class TenantIdDecorator extends C3Decorator {

    @JsonIgnore
    public static final String type = "TenantIdDecorator";

    public TenantIdDecorator() {
        this.targets = List.of(DecoratorTarget.FIELD);
    }
}
