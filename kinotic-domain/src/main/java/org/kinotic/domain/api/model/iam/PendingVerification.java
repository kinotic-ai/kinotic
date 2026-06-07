package org.kinotic.domain.api.model.iam;

import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * A short-lived, single-use record consumed by clicking a tokenized link: an email/password
 * verification that has not yet been completed. Implementations carry an opaque
 * {@link #getVerificationToken() token} and a hard {@link #getExpiresAt() expiry}, which together
 * let a repository look the record up and reject it once it has lapsed.
 */
public interface PendingVerification extends Identifiable<String> {

    /** Opaque single-use token carried in the completion URL. */
    String getVerificationToken();

    /** Hard expiry after which the token is invalid. */
    Date getExpiresAt();
}
