package org.kinotic.core.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that receives every event of its parameter type published by any {@link Emitter}
 * anywhere in the cluster.
 * <p>
 * The method must take exactly one parameter — the event, a concrete class — and return {@code void}.
 * The fully qualified name of the parameter type identifies the event stream, so consumers and
 * {@link Emitter}s pair through the event type alone.
 * <p>
 * Delivery is fan-out, never round-robin: every {@link Consumer} method on every node receives its
 * own copy of every event. An exception thrown by one consumer is logged and does not affect
 * delivery to any other consumer.
 * <p>
 * Events are delivered on a Vert.x event loop — never block in a consumer method.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Consumer {
}
