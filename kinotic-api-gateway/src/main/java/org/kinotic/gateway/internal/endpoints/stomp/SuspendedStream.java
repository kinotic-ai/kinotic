package org.kinotic.gateway.internal.endpoints.stomp;

import org.kinotic.core.api.event.CRI;

/**
 * A stream the gateway suspended while its client caught up.
 *
 * @param originCri   the service producing the stream, where its resume is sent
 * @param suspendedAt when the suspend was sent, epoch milliseconds
 */
public record SuspendedStream(CRI originCri, long suspendedAt) {
}
