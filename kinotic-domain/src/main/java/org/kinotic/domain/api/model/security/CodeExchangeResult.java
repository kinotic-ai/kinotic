package org.kinotic.domain.api.model.security;

import org.kinotic.core.api.security.Participant;

/**
 * Result of redeeming a PKCE authorization code: the participant whose consent created the grant, in the
 * scope it was signed in to, and the client identity the grant was issued to, as the token endpoint needs
 * both to issue tokens for the client acting on the user's behalf in that scope.
 *
 * @param approver   the participant that approved the consent; able to act in its scope at redemption time
 * @param clientId   the client's CIMD {@code client_id} URL the grant was bound to
 * @param clientName the display name the client's metadata document claims
 */
public record CodeExchangeResult(Participant approver, String clientId, String clientName) {}
