package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import tools.jackson.databind.node.ObjectNode;

/**
 * Authorizes an external request against the function it addresses, once, where the platform stamps the caller
 * onto it: a client's request at the gateway, and a tool call at the MCP endpoint. The check is the one the
 * function's contract in the service directory declares; a function its contract marks unchecked passes on the
 * zone rules alone, and a function no contract covers is refused.
 */
public interface RequestAuthorizer {

    /**
     * Authorizes a client's request, whose body carries the function's arguments in order.
     *
     * @param cri         the request's destination, whose base resource, path and version name the function
     * @param participant the caller the gateway authenticated
     * @param contentType the body's content type
     * @param body        the body, a JSON array of the arguments, read only as far as the argument the check names
     * @return completes when the request may proceed; fails with an {@link AuthorizationException} reading
     *         "Not authorized", followed by the reason when {@code kinotic.debug} is on, when the caller holds no
     *         permission for it, the request names no object to check, the object the check reads from names a
     *         property twice, or no contract covers the function, and with the engine's failure when the check
     *         cannot be made
     */
    Future<Void> authorize(CRI cri, Participant participant, String contentType, byte[] body);

    /**
     * Authorizes a tool call, whose arguments are named.
     *
     * @param cri         the destination the tool addresses, whose base resource, path and version name the function
     * @param participant the caller the MCP endpoint authenticated
     * @param arguments   the call's arguments by parameter name
     * @return completes when the call may proceed; fails with an {@link AuthorizationException} reading
     *         "Not authorized", followed by the reason when {@code kinotic.debug} is on, when the caller holds no
     *         permission for it, the call names no object to check, or no contract covers the function, and with
     *         the engine's failure when the check cannot be made
     */
    Future<Void> authorize(CRI cri, Participant participant, ObjectNode arguments);
}
