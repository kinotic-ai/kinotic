package org.kinotic.core.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose {@link reactor.core.publisher.Flux} feeds the cluster-wide event fabric.
 * <p>
 * The framework is the method's only caller: at bean initialization it invokes the method once,
 * subscribes to the returned {@code Flux}, and publishes every element to all {@link Consumer}s of
 * the element type on every node, including this one. Keep the method package-private so the
 * framework remains its only caller.
 * <p>
 * The method must take no parameters and return a hot shared {@code Flux<T>} where {@code T} is a
 * concrete class; the fully qualified name of {@code T} identifies the event stream, so emitters and
 * {@link Consumer}s pair through the event type alone.
 * <p>
 * Delivery is fire-and-forget and at-most-once: there is no acknowledgement, no replay, and elements
 * published while a node is unreachable are lost.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Emitter {
}
