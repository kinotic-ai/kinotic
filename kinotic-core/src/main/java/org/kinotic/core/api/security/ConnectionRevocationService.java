package org.kinotic.core.api.security;

import io.vertx.core.Future;

/**
 * Ends the realtime connections an identity or a browser login holds, on every node of the cluster. A connection
 * is authenticated once, when it is established, and carries its participant for its life, so a disabled or removed
 * identity, or a login that was logged out, keeps its authority on the connections it already holds until they are
 * ended here.
 */
public interface ConnectionRevocationService {

    /**
     * Ends every connection authenticated as the identity.
     *
     * @param identityId the identity whose connections end
     * @return a future completed once the revocation is published
     */
    Future<Void> revokeIdentity(String identityId);

    /**
     * Ends every connection a browser login established.
     *
     * @param loginId the login, as {@link SessionBinding#loginId} names it
     * @return a future completed once the revocation is published
     */
    Future<Void> revokeLogin(String loginId);

}
