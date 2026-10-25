package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.domain.api.exceptions.ExistingUserException;
import org.kinotic.domain.api.exceptions.PendingMemberException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.EmailTemplate;
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
     * verification link. The organization name and password are collected later, at completion. Fails with
     * {@link ExistingUserException} when the email has an organization user someone signs in as, with
     * {@link PendingMemberException} when it has one an administrator added that nobody signs in as yet, and when
     * a sign-up is already pending for the email. A pending sign-up that has expired is replaced.
     * <p>
     * When {@code kinotic.domain.email.enabled} is {@code false}, no email is sent and the verification link is
     * returned for the caller to open, so the address is never proven to be the caller's; an email/password sign-up
     * already pending for the email then returns its link again.
     *
     * @param email           the email to verify
     * @param displayName     the founder's display name
     * @param emailTemplate   the verification email to send in place of Kinotic's, whose UI's {@code /signup/verify}
     *                        page the link opens, or {@code null} for Kinotic's, which opens the portal's
     * @return a future emitting the verification link when the UI shows it, or {@code null} once it is emailed
     * @throws IllegalArgumentException when {@code emailTemplate} is not valid, as {@link EmailTemplate} describes
     */
    Future<String> initiateLocalSignUp(String email, String displayName, EmailTemplate emailTemplate);

    /**
     * Stores a pending sign-up for an identity already verified by an OIDC provider, returning it
     * with its {@code id} and {@code verificationToken} populated. The caller supplies the verified
     * identity (and, for an existing-org registration, the {@code organizationId}). Fails as
     * {@link #initiateLocalSignUp} does when the identity, or its verified email, has an organization user.
     */
    Future<PendingSignUp> createOidcPending(PendingSignUp pending);

    /**
     * Completes an email/password sign-up: validates the token, creates the organization with the
     * given name (failing if it is taken), the founder's organization user with its password and its
     * membership of the organization, then deletes the pending record.
     *
     * @return the founder as the new organization's participant, for the session to sign in
     */
    Future<Participant> completeLocalSignUp(String token, String orgName, String orgDescription, String password);

    /**
     * Completes an OIDC sign-up by creating a new organization with the given name (failing if it is
     * taken), the founder's organization user bound to the verified identity and its membership of the
     * organization, then deleting the pending record.
     *
     * @return the founder as the new organization's participant, for the session to sign in
     */
    Future<Participant> completeOidcWithNewOrg(String token, String orgName, String orgDescription);
}
