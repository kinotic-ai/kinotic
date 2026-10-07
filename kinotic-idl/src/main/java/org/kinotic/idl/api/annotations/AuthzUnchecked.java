package org.kinotic.idl.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a function of an {@link AuthzResource} service that is served with no authorization check: any caller
 * the service's zone admits may call it, and the service itself is responsible for answering only what the
 * caller may see or do. It is for a function that has no resource to check, such as a listing of the caller's
 * own grants. A function carrying this and {@link AuthzCheck} fails the service's registration.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthzUnchecked {
}
