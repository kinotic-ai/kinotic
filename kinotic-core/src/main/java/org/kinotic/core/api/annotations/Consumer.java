package org.kinotic.core.api.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that receives every event of its parameter type that
 * {@link org.kinotic.core.api.event.EventService#publish(Object)} or
 * {@link org.kinotic.core.api.event.EventService#publishConfirmed(Object)} sends, from any node in the cluster.
 * <p>
 * The method must take exactly one parameter, the event, a concrete class Jackson can read, and return {@code void}
 * or a {@link io.vertx.core.Future}. The fully qualified name of the parameter type identifies the events it
 * receives, so publishers and consumers pair through the event type alone.
 * <p>
 * Delivery is fan-out, never round-robin: every {@link Consumer} method on every node receives its own copy of
 * every event. An event sent with {@code publish} is handed to each consumer independently, and a consumer that
 * throws or returns a failed future affects no other. An event sent with {@code publishConfirmed} counts as handled
 * on a node once every consumer there has returned, and once the future a consumer returns has completed; a
 * consumer that throws or returns a failed future fails the delivery to its node, which the publisher retries, so
 * such a consumer must be idempotent.
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
