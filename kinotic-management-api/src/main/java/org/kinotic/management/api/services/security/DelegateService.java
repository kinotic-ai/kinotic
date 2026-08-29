package org.kinotic.management.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.DelegateSession;
import org.kinotic.domain.api.model.security.DelegatingParticipantIdentity;

import java.util.List;

/**
 * The signed-in user's view of the clients authorized to act on their behalf — CLI installs
 * and MCP hosts — with the sessions each one holds, and revocation of a single session or a
 * whole client. Every operation is bound to the calling user: only a delegate's owner can
 * see or revoke it, and only a person (never a delegate) may call these at all.
 */
@Publish
public interface DelegateService {

    /**
     * Lists the delegates authorized on the calling user's behalf, revoked ones included
     * (disabled entries show as revoked until re-approved).
     */
    Future<Page<DelegatingParticipantIdentity>> findMyDelegates(Pageable pageable, Participant participant);

    /**
     * Lists the live sessions of one of the calling user's delegates — one entry per
     * refresh-token family, labeled when the client supplied a device name.
     *
     * @param delegateId a delegate owned by the calling user
     */
    Future<List<DelegateSession>> findSessions(String delegateId, Participant participant);

    /**
     * Ends a single session of one of the calling user's delegates. The session's tokens can
     * no longer be refreshed; an unexpired access token runs out on its own short TTL.
     *
     * @param delegateId a delegate owned by the calling user
     * @param familyId   the session to end, from {@link #findSessions}
     */
    Future<Void> revokeSession(String delegateId, String familyId, Participant participant);

    /**
     * Revokes one of the calling user's delegates entirely: the client is cut off on its next
     * request, and every session it holds is ended. A later re-approval through the normal
     * consent flow restores access.
     *
     * @param delegateId a delegate owned by the calling user
     */
    Future<Void> revokeDelegate(String delegateId, Participant participant);
}
