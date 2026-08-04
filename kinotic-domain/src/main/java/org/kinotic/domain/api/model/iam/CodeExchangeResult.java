package org.kinotic.domain.api.model.iam;

/**
 * Result of redeeming a PKCE authorization code: the user whose consent created the grant,
 * and the client identity the grant was issued to, as the token endpoint needs both to issue
 * tokens for the client acting on the user's behalf.
 *
 * @param approver   the user who approved the consent; enabled at redemption time
 * @param clientId   the client's CIMD {@code client_id} URL the grant was bound to
 * @param clientName the display name the client's metadata document claims
 */
public record CodeExchangeResult(UserParticipantIdentity approver, String clientId, String clientName) {}
