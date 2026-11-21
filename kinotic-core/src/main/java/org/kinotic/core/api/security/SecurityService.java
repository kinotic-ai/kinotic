

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
     * @param context what the entry point asks for beside the credentials: the surface it serves, which a token
     *                must have been minted for
     * @return a {@link Future} completing with a {@link Participant} if authentication was successful or an error if authentication failed.
     *         The returned {@link Future} is completed on the Vert.x context of the caller.
     *         WARNING: do not store sensitive information in {@link Participant} as it will be sent to receivers of requests sent by the {@link Participant}
     */
    Future<Participant> authenticate(Map<String, String> authenticationInfo, AuthenticationContext context);

    /**
     * Re-establishes a participant that {@link #authenticate} returned earlier and a browser session has held since,
     * as its identity stands now.
     * @param participant the participant the session holds
     * @return a {@link Future} completing with the participant its identity acts as now, or failed with an
     *         {@link org.kinotic.core.api.exceptions.AuthenticationException} when the identity no longer exists or
     *         may no longer act as that participant.
     *         The returned {@link Future} is completed on the Vert.x context of the caller.
     */
    Future<Participant> reauthenticate(Participant participant);

}
