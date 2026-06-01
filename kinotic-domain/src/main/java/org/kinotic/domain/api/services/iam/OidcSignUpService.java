package org.kinotic.domain.api.services.iam;

import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.OidcPendingSignUp;
import org.kinotic.domain.api.model.iam.UserProvisioningMode;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Stores and consumes the short-lived {@link OidcPendingSignUp} records produced by the OIDC
 * callback in {@link UserProvisioningMode#REGISTRATION_REQUIRED} mode.
 */
public interface OidcSignUpService {

    /**
     * Persists a new pending sign-up and returns it with its {@code id} and
     * {@code verificationToken} populated.
     */
    CompletableFuture<OidcPendingSignUp> create(OidcPendingSignUp registration);

    /**
     * Finds a pending sign-up by verification token, or returns null if none matches.
     * Does not delete — call {@link #complete} or {@link #completeWithNewOrg} to consume.
     */
    CompletableFuture<OidcPendingSignUp> findByToken(String verificationToken);

    /**
     * Consumes a pending sign-up whose target org is already known: validates the token, applies
     * the user-supplied extras to a new {@link IamUser}, deletes the pending record, and returns
     * the created user. The pending sign-up must already have its {@code organizationId} populated.
     *
     * @param verificationToken one-time token from the pending sign-up
     * @param finalizer         applies user-supplied overrides (e.g. chosen display name) to the
     *                          {@link IamUser} before it is saved
     */
    CompletableFuture<IamUser> complete(String verificationToken, Consumer<IamUser> finalizer);

    /**
     * Consumes a pending sign-up that has no org context yet (the social-signup path): creates a
     * new Organization from the supplied name + description, creates the {@link IamUser} as its
     * admin with the verified OIDC identity, and deletes the pending record.
     *
     * @param verificationToken one-time token from the pending sign-up
     * @param orgName           name for the new organization
     * @param orgDescription    description for the new organization (may be null)
     */
    CompletableFuture<IamUser> completeWithNewOrg(String verificationToken, String orgName, String orgDescription);
}
