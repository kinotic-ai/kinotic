package org.kinotic.core.api.security;

import io.vertx.core.Future;

/**
 * Ends the realtime connections an identity holds, or that a browser login established, on every gateway node of
 * the cluster. A connection is authenticated once, when it is established, and carries its participant for its
 * life, so a disabled or removed identity, or a login its page has ended, keeps its authority on the connections it
 * already holds until they are ended here.
 */
public interface ConnectionRevocationService {

    /**
     * Ends every connection authenticated as the identity, a user's or a machine's, wherever it connected from.
     *
     * @param identityId the identity whose connections end
     * @return a future completed once the revocation is published
     */
    Future<Void> revoke(String identityId);

    /**
     * Ends every connection established on the browser login, wherever it connected from.
     *
     * @param loginId the {@link ConnectedInfo#getLoginId()} of the login whose connections end
     * @return a future completed once the revocation is published
     */
    Future<Void> revokeLogin(String loginId);

}
