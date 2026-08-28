package org.kinotic.core.internal;

import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;

/**
 * Teardown ledger for one bean's event fabric wiring: everything {@link EventFabric#unwire(Object)}
 * must undo when the bean is destroyed.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
class BeanWiring {

    /** Emitter subscriptions to dispose, so a destroyed bean's stream stops publishing. */
    final List<Disposable> uplinks = new ArrayList<>();

    /** Consumer targets to remove; a type's shared {@link Downlink} unregisters with its last target. */
    final List<ConsumerRegistration> consumerTargets = new ArrayList<>();
}
