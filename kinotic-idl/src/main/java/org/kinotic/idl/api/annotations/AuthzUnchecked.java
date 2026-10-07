package org.kinotic.idl.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a function of an {@link AuthzResource} service, or every function of a service declaring no resource,
 * as served with no authorization check: any caller the service's zone admits may call it, and the service
 * itself is responsible for answering only what the caller may see or do. It is for a function that has no
 * resource to check, such as a listing of the caller's own grants, and for a service whose every function
 * answers for the caller alone, such as the caller's own profile. The mark is the only way a function is served
 * unchecked: one whose contract neither checks it nor marks it is refused. A function carrying this and
 * {@link AuthzCheck} fails the service's registration, as does a service carrying this and {@link AuthzResource}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthzUnchecked {
}
