package org.kinotic.core.api.security;

import io.vertx.core.Future;

/**
 * Ends the realtime connections an identity holds, on every gateway node of the cluster. A connection is
 * authenticated once, when it is established, and carries its participant for its life, so a disabled or removed
 * identity keeps its authority on the connections it already holds until they are ended here.
 */
public interface ConnectionRevocationService {

    /**
     * Ends every connection authenticated as the identity, a user's or a machine's, wherever it connected from.
     *
     * @param identityId the identity whose connections end
     * @return a future completed once the revocation is published
     */
    Future<Void> revoke(String identityId);

}
