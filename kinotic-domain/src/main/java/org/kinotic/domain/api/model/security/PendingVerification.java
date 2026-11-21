package org.kinotic.domain.api.model.security;

import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * A short-lived, single-use record backing a token-verified flow such as sign-up or invite.
 * It carries the opaque token presented in the completion URL and the hard expiry after which
 * that token is invalid. Implementations are consumed — looked up by token, then deleted — once
 * their flow completes.
 */
public interface PendingVerification extends Identifiable<String> {

    /** The opaque single-use token carried in the completion URL. */
    String getVerificationToken();

    /** The hard expiry after which {@link #getVerificationToken()} is invalid. */
    Date getExpiresAt();

    /**
     * The time, in epoch milliseconds, until which one completion of the flow holds this record, so that of several
     * completions presenting the token at once only one proceeds; {@code null} while no completion holds it.
     */
    Long getClaimedUntil();
}
