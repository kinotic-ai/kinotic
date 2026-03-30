package org.kinotic.auth.api.annotations;

import java.lang.annotation.*;

/**
 * Container annotation for repeatable {@link AbacPolicy} annotations.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AbacPolicies {
    AbacPolicy[] value();
}
