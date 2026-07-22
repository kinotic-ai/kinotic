package org.kinotic.core.api.event;

/**
 * Signals a gap in the stream of {@link ServiceListenerChange}s: changes may have been missed, so any state derived
 * from them is stale until rebuilt from a fresh snapshot.
 */
public record ServiceListenerContinuityLost() implements ServiceListenerEvent {
}
