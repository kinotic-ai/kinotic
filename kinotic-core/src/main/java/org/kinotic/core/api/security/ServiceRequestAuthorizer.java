package org.kinotic.core.api.security;

import io.vertx.core.Future;
import org.kinotic.core.api.event.Event;

/** Authorizes an authenticated external invocation before it is dispatched. */
public interface ServiceRequestAuthorizer {
    Future<Void> authorize(Event<byte[]> event);
}
