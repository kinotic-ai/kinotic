package org.kinotic.domain.api.model.iam;

import java.util.Date;

/**
 * A live session of an identity: one refresh-token family, described without exposing any
 * token material. Listed so an owner can see where their delegates are signed in and revoke
 * a single session.
 *
 * @param familyId        identifies the session for revocation
 * @param label           the label supplied when the session started (e.g. a device name),
 *                        or null when the client supplied none
 * @param lastRefreshedAt when the session's current token was minted — issuance, or the
 *                        latest rotation
 * @param expiresAt       when the session ends on its own if never refreshed again
 */
public record DelegateSession(String familyId, String label, Date lastRefreshedAt, Date expiresAt) {}
