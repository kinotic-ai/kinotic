package org.kinotic.core.internal.api.event.fabric;

import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.event.EventConsumer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The receiving side of one event type on this node: a single shared bus consumer whose events are
 * dispatched to every registered {@link org.kinotic.core.api.annotations.Consumer} method of the type.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
@RequiredArgsConstructor
class Downlink {

    final EventConsumer busConsumer;

    /** Copy-on-write: mutated only at (un)wiring, iterated on every dispatch. */
    final List<MethodTarget> targets = new CopyOnWriteArrayList<>();
}
