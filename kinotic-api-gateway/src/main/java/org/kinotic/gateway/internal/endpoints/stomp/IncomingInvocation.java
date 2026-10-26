package org.kinotic.gateway.internal.endpoints.stomp;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Metadata;

/**
 * A request the client on a connection sent into the cluster and is still waiting on: the headers an
 * answer to it needs, and what its stream has shown about being suspended.
 */
@Getter
@Setter
@RequiredArgsConstructor
public class IncomingInvocation {

    /**
     * The reply-to and {@code __} headers of the request, which any answer sent in the reply's place carries.
     */
    private final Metadata replyMetadata;

    /**
     * The address the client sent the request to, where control events for its stream are sent.
     */
    private final CRI destination;

    /**
     * Whether a value of the stream answering the request has arrived; only the first one may declare the
     * stream suspendable.
     */
    private boolean valueSeen;

    /**
     * Whether the stream answering the request may be suspended: its first value declared it, and it has not
     * kept sending after a suspend.
     */
    private boolean suspendable;

    /**
     * Whether a warning was logged that the stream cannot be suspended while its client is behind.
     */
    private boolean unsuspendableWarned;

}
