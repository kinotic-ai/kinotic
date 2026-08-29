package org.kinotic.core.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method of a {@link Scope}d {@link Publish}ed service that any instance may answer,
 * because its result does not depend on which instance executes it - typically a read of
 * shared state rather than of the instance's own.
 *
 * A scoped service normally listens only at its scoped address, so every invocation must name
 * an instance. When at least one method carries {@link ScopeOptional}, each instance also
 * listens on the service's shared unscoped address, where only the annotated methods may be
 * invoked: an unscoped invocation of any other method is rejected, since it would execute on
 * whichever instance happened to receive it.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ScopeOptional {
}
