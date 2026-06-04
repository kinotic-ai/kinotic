package org.kinotic.domain.api.services.iam;

import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.PendingSignUp;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Drives organization sign-up — creating a new organization and its admin {@link IamUser} —
 * through a short-lived {@link PendingSignUp} record, for both email/password and OIDC. Two
 * phases: <em>initiate</em> stashes the record and verifies the email (a link for local, the IdP
 * callback for OIDC); <em>complete</em> consumes the record and creates the organization + admin.
 * <p>
 * Application end-user sign-up (a user within an existing application/tenant) is a separate
 * concern with its own service.
 */
public interface OrganizationSignUpService {

    /**
     * Starts an email/password sign-up: validates and stores a pending record and emails a
     * verification link. The organization name and password are collected later, at completion.
     */
    CompletableFuture<Void> initiateLocalSignUp(String email, String displayName);

    /**
     * Stores a pending sign-up for an identity already verified by an OIDC provider, returning it
     * with its {@code id} and {@code verificationToken} populated. The caller supplies the verified
     * identity (and, for an existing-org registration, the {@code organizationId}).
     */
    CompletableFuture<PendingSignUp> createOidcPending(PendingSignUp pending);

    /**
     * Completes an email/password sign-up: validates the token, creates the organization with the
     * given name (failing if it is taken), its admin user, and the password credential, then
     * deletes the pending record.
     *
     * @return the new organization's id
     */
    CompletableFuture<String> completeLocalSignUp(String token, String orgName, String orgDescription, String password);

    /**
     * Completes an OIDC sign-up by creating a new organization with the given name (failing if it
     * is taken), making the verified identity its admin, then deleting the pending record.
     */
    CompletableFuture<IamUser> completeOidcWithNewOrg(String token, String orgName, String orgDescription);

    /**
     * Completes an OIDC registration into the organization already recorded on the pending sign-up:
     * creates the member user (applying {@code finalizer} for user-supplied overrides) and deletes
     * the pending record.
     */
    CompletableFuture<IamUser> completeOidc(String token, Consumer<IamUser> finalizer);
}
