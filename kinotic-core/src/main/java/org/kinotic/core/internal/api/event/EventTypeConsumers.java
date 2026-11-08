package org.kinotic.core.internal.api.event;

import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.event.EventConsumer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The receiving side of one event type and delivery on this node: a single shared bus consumer, and every registered
 * {@link org.kinotic.core.api.annotations.KinoticConsumer} method of the type that declared that delivery.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
@RequiredArgsConstructor
class EventTypeConsumers {
    final Class<?> eventType;
    final EventConsumer busConsumer;
    /** Copy-on-write: mutated only at (un)registration, iterated on every delivery. */
    final List<ConsumerMethod> methods = new CopyOnWriteArrayList<>();
}
