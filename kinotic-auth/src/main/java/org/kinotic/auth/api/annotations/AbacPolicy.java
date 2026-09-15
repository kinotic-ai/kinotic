package org.kinotic.auth.api.annotations;

import java.lang.annotation.*;

/**
 * Defines an ABAC policy expression on a published service method.
 * <p>
 * The value is a policy expression string using the ABAC policy language:
 * <pre>
 * {@code @AbacPolicy("participant.role contains 'finance' and order.amount < 50000")}
 * </pre>
 * <p>
 * Identifiers in the expression are resolved against method parameter names.
 * {@code participant} always refers to the authenticated participant,
 * and {@code context} refers to the request environment.
 * All other root identifiers are matched to method parameter names.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AbacPolicy {

    /**
     * The ABAC policy expression string.
     */
    String value();
}
