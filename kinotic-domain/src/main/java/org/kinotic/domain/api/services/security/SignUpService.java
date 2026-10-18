package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.PendingSignUp;

/**
 * Drives every sign-up through one short-lived {@link PendingSignUp} record. Two phases:
 * <em>initiate</em> stashes the record and verifies the email (a link for local, the IdP callback
 * for OIDC); <em>complete</em> consumes the record and creates the {@link UserParticipantIdentity} — and, for a
 * brand-new organization, the organization too.
 */
public interface SignUpService {

    /**
     * Starts an email/password sign-up: validates and stores a pending record and emails a
     * verification link. The organization name and password are collected later, at completion.
     */
    Future<Void> initiateLocalSignUp(String email, String displayName);

    /**
     * Stores a pending sign-up for an identity already verified by an OIDC provider, returning it
     * with its {@code id} and {@code verificationToken} populated. The caller supplies the verified
     * identity (and, for an existing-org registration, the {@code organizationId}).
     */
    Future<PendingSignUp> createOidcPending(PendingSignUp pending);

    /**
     * Completes an email/password sign-up: validates the token, creates the organization with the
     * given name (failing if it is taken), the founder's account with its password and its membership
     * of the organization, then deletes the pending record.
     *
     * @return the founder as the new organization's participant, for the session to sign in
     */
    Future<Participant> completeLocalSignUp(String token, String orgName, String orgDescription, String password);

    /**
     * Completes an OIDC sign-up by creating a new organization with the given name (failing if it is
     * taken), the founder's account bound to the verified identity and its membership of the
     * organization, then deleting the pending record.
     *
     * @return the founder as the new organization's participant, for the session to sign in
     */
    Future<Participant> completeOidcWithNewOrg(String token, String orgName, String orgDescription);
}
