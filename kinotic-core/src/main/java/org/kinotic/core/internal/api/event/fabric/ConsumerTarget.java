package org.kinotic.core.internal.api.event.fabric;

/**
 * One receiver of an event type's {@link Downlink}: a {@link org.kinotic.core.api.annotations.Consumer} method
 * bound to its bean, or a handler registered directly.
 */
interface ConsumerTarget {

    /**
     * @param element the event, an instance of the target's event type
     * @throws Throwable whatever the receiver throws; the downlink logs it and delivers to the next target
     */
    void accept(Object element) throws Throwable;

}
