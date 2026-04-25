package org.kinotic.os.api.services.iam;

import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.api.model.iam.PendingRegistration;

import java.util.concurrent.CompletableFuture;

/**
 * Stores and consumes short-lived pending registrations produced by the OIDC callback in
 * {@link org.kinotic.os.api.model.iam.UserProvisioningMode#REGISTRATION_REQUIRED} mode.
 * <p>
 * Not a {@code @Publish} service — called in-process from the gateway's HTTP handlers.
 */
public interface PendingRegistrationService {

    /**
     * Persists a new pending registration and returns it with {@link PendingRegistration#getId()}
     * and {@link PendingRegistration#getVerificationToken()} populated.
     */
    CompletableFuture<PendingRegistration> create(PendingRegistration registration);

    /**
     * Finds a pending registration by verification token, or returns null if none matches.
     * Does not delete — call {@link #complete} to consume.
     */
    CompletableFuture<PendingRegistration> findByToken(String verificationToken);

    /**
     * Consumes a pending registration: finds by token, validates it's not expired, applies the
     * user-supplied extras to a new {@link IamUser}, deletes the pending record, and returns
     * the created user. Fails if the token is missing, already consumed, or expired.
     * <p>
     * The pending registration must already have its target scope populated (this is the
     * REGISTRATION_REQUIRED path, where the org is known up front).
     *
     * @param verificationToken one-time token from the pending registration
     * @param finalizer         applies user-supplied overrides (e.g. chosen display name) to
     *                          the {@link IamUser} before it is saved. Receives the user
     *                          already populated with OIDC-sourced fields.
     */
    CompletableFuture<IamUser> complete(String verificationToken, java.util.function.Consumer<IamUser> finalizer);

    /**
     * Consumes a pending registration that has no org context yet (the social-signup path):
     * creates a new {@link org.kinotic.os.api.model.Organization} from the supplied name +
     * description, then creates the {@link IamUser} as the org's admin with the verified
     * OIDC identity, and deletes the pending record. Fails if the token is missing, already
     * consumed, or expired.
     *
     * @param verificationToken one-time token from the pending registration
     * @param orgName           name for the new organization
     * @param orgDescription    description for the new organization (may be null)
     */
    CompletableFuture<IamUser> completeWithNewOrg(String verificationToken, String orgName, String orgDescription);
}
