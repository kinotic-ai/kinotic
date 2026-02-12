

package org.kinotic.rpc.api.security;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
     * @return a {@link CompletableFuture} completing with a {@link DefaultParticipant} if authentication was successful or an error if authentication failed
     *         WARNING: do not store sensitive information in {@link Participant} as it will be sent to receivers of requests sent by the {@link Participant}
     */
    CompletableFuture<Participant> authenticate(Map<String, String> authenticationInfo);

}
