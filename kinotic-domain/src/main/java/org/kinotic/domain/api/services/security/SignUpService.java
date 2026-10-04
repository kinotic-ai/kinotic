package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
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
     * given name (failing if it is taken), its admin user, and the password credential, then
     * deletes the pending record.
     *
     * @return the new organization's admin user
     */
    Future<UserParticipantIdentity> completeLocalSignUp(String token, String orgName, String orgDescription, String password);

    /**
     * Completes an OIDC sign-up by creating a new organization with the given name (failing if it
     * is taken), making the verified identity its admin, then deleting the pending record.
     */
    Future<UserParticipantIdentity> completeOidcWithNewOrg(String token, String orgName, String orgDescription);

    /**
     * Starts an email/password sign-up of a new customer of an application that offers
     * {@link org.kinotic.domain.api.model.OnboardingMechanism#TENANT_SIGN_UP}: validates and stores a pending
     * record and emails a verification link into the application's primary UI. The tenant's name and the
     * password are collected later, at completion.
     *
     * @param applicationKey the application signed up to
     * @param email          the customer's email, which no user of the application has yet
     * @param displayName    the customer's name
     */
    Future<Void> initiateTenantSignUp(ApplicationKey applicationKey, String email, String displayName);

    /**
     * Completes a tenant sign-up: validates the token, creates the tenant with the given name (failing if an
     * application tenant of that name exists), the customer as its first user with the password, and makes
     * the customer the tenant's administrator in the application's store, then deletes the pending record.
     *
     * @return the tenant's first user
     */
    Future<UserParticipantIdentity> completeTenantSignUp(String token, String tenantName, String password);
}
