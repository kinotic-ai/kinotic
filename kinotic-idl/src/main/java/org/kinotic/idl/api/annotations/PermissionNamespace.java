package org.kinotic.idl.api.annotations;

import java.lang.annotation.*;

/** Supplies the prefix for a service's relative permission names. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PermissionNamespace {
    String value();
}
