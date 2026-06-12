package org.kinotic.domain.api.services.iam;

/**
 * Thrown when an invitation is accepted with an OIDC identity whose IdP-verified email does
 * not match the invited email. The invitation is not consumed — the invitee can retry with
 * a different provider or account, or accept with a password instead.
 */
public class InviteEmailMismatchException extends IllegalArgumentException {

    public InviteEmailMismatchException(String message) {
        super(message);
    }
}
