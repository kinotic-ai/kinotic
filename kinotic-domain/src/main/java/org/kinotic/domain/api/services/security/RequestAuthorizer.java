package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;

/**
 * Authorizes an external request against the function it addresses, once, where the gateway stamps the caller
 * onto it. The check is the one the function's contract in the service directory declares; a function the
 * contract marks unchecked passes on the zone rules alone, and a function no contract covers is refused.
 */
public interface RequestAuthorizer {

    /**
     * @param cri         the request's destination, whose base resource, path and version name the function
     * @param participant the caller the gateway authenticated
     * @param contentType the body's content type
     * @param body        the body, read only as far as the id the check names
     * @return completes when the request may proceed; fails with an {@link AuthorizationException} reading
     *         "Not authorized" when the caller holds no permission for it, the request names no object to check,
     *         or no contract covers the function, and with the engine's failure when the check cannot be made
     */
    Future<Void> authorize(CRI cri, Participant participant, String contentType, byte[] body);
}
