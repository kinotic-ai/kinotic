package org.kinotic.idl.api.annotations;

import java.lang.annotation.*;

/** Declares the permission required to invoke a published function. */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {
    /** Relative permission; an empty value uses the function name. */
    String value() default "";
    String label() default "";
    /** Whether an application tenant administrator can delegate this permission. */
    boolean tenantDelegable() default false;
}
