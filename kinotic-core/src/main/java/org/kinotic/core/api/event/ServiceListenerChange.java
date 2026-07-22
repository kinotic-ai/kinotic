package org.kinotic.core.api.event;

/**
 * A change in listener registration for a service address, delivered as a stream of deltas.
 * @param address the base resource address whose listener registration changed
 * @param status {@link ListenerStatus#ACTIVE} when a listener registered, {@link ListenerStatus#INACTIVE} when one left
 */
public record ServiceListenerChange(String address, ListenerStatus status) implements ServiceListenerEvent {
}
