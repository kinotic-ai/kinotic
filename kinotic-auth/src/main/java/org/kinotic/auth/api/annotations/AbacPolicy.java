package org.kinotic.auth.api.annotations;

import java.lang.annotation.*;

/**
 * Defines an ABAC policy expression on a published service method.
 * <p>
 * The value is a policy expression string using the ABAC policy language:
 * <pre>
 * {@code @AbacPolicy("principal.role contains 'finance' and order.amount < 50000")}
 * </pre>
 * <p>
 * Identifiers in the expression are resolved against method parameter names.
 * {@code principal} always refers to the authenticated participant,
 * and {@code context} refers to the request environment.
 * All other root identifiers are matched to method parameter names.
 * <p>
 * This annotation is repeatable: multiple policies are combined with AND semantics
 * (all must be satisfied).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(AbacPolicies.class)
public @interface AbacPolicy {

    /**
     * The ABAC policy expression string.
     */
    String value();
}
