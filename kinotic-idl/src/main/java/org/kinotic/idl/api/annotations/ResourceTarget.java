package org.kinotic.idl.api.annotations;

import java.lang.annotation.*;

/** Identifies the resource type and the named argument containing its id. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ResourceTarget {
    /** A method inherits an empty type from its service declaration. */
    String type() default "";
    /** Named argument, optionally followed by object fields; empty means the entire scope. */
    String idArgument() default "";
}
