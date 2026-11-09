package org.kinotic.core.api.annotations;

import org.kinotic.core.api.event.EventDelivery;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that receives every event of its parameter type that
 * {@link org.kinotic.core.api.event.EventService#publish(Object)} sends, from any node in the cluster, delivered as
 * its {@link #delivery()} declares.
 * <p>
 * The method must take exactly one parameter, the event, a concrete class Jackson can read, and return {@code void}
 * or a {@link io.vertx.core.Future}. The fully qualified name of the parameter type identifies the events it
 * receives, so publishers and consumers pair through the event type alone.
 * <p>
 * Delivery is fan-out, never round-robin: every {@link KinoticConsumer} method on every node receives its own copy of
 * every event, and a consumer that throws or returns a failed future affects no other consumer. A
 * {@link EventDelivery#CONFIRMED} consumer has handled the event once it has returned and the future it returns has
 * completed; one that throws or returns a failed future fails the delivery to its node, which is retried.
 * <p>
 * Events are delivered on a Vert.x event loop — never block in a consumer method.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface KinoticConsumer {

    /**
     * What the method needs from the delivery of its events.
     */
    EventDelivery delivery() default EventDelivery.BEST_EFFORT;
}
