package org.kinotic.core.api.event;

/**
 * An event observed on the service ({@code srv://}) address space: either a {@link ServiceListenerChange} for one
 * address, or a {@link ServiceListenerContinuityLost} signaling that changes may have been missed.
 */
public sealed interface ServiceListenerEvent permits ServiceListenerChange, ServiceListenerContinuityLost {
}
