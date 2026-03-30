package org.kinotic.auth.api.decorators;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.idl.api.schema.decorators.C3Decorator;
import org.kinotic.idl.api.schema.decorators.DecoratorTarget;

import java.util.List;

/**
 * Decorator that attaches an ABAC policy expression to an entity definition,
 * entity field, or named query.
 * <p>
 * The {@link #expression} is a policy expression string using the ABAC policy language:
 * <pre>
 * new AbacPolicyDecorator().setExpression("principal.department == entity.department and entity.status in ['active', 'pending']")
 * </pre>
 * <p>
 * In entity definition context, {@code entity} refers to the document being operated on,
 * and {@code principal} refers to the authenticated participant.
 */
@Getter
@Setter
@Accessors(chain = true)
public class AbacPolicyDecorator extends C3Decorator {

    @JsonIgnore
    public static final String type = "AbacPolicyDecorator";

    private String expression = "";

    public AbacPolicyDecorator() {
        this.targets = List.of(DecoratorTarget.TYPE, DecoratorTarget.FIELD, DecoratorTarget.FUNCTION);
    }
}
