

package org.kinotic.core.api.security;

import io.vertx.core.Future;

import java.util.Map;

/**
 * {@link SecurityService} provides core security functionality.
 *
 *
 * Created by navid on 2019-05-01.
 */
public interface SecurityService {

    /**
     * Check if a given participant can authenticate
     * @param authenticationInfo a {@link Map} containing the authentication information
     * @return a {@link Future} completing with a {@link Participant} if authentication was successful or an error if authentication failed.
     *         The returned {@link Future} is completed on the Vert.x context of the caller.
     *         WARNING: do not store sensitive information in {@link Participant} as it will be sent to receivers of requests sent by the {@link Participant}
     */
    Future<Participant> authenticate(Map<String, String> authenticationInfo);

}
