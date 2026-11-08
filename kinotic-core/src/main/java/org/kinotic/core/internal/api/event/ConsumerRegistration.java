package org.kinotic.core.internal.api.event;

/**
 * One {@link org.kinotic.core.api.annotations.KinoticConsumer} method's membership in the {@link EventTypeConsumers} of
 * its address, recorded per bean so {@link ConsumerMethodRegistry#unregister(Object)} removes exactly this method.
 *
 * Created by Navid Mitchell on 2026-08-23.
 */
record ConsumerRegistration(String address, ConsumerMethod method) {
}
