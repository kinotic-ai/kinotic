package org.kinotic.domain.api.services.iam;

import org.kinotic.domain.api.model.iam.LocalPendingSignUp;

import java.util.concurrent.CompletableFuture;

/**
 * Handles email/password organization sign-up with email verification.
 * <p>
 * Two-step flow:
 * <ol>
 *   <li>{@link #initiateSignUp} — stores the pending sign-up and sends a verification email.
 *       No password is collected at this stage.</li>
 *   <li>{@link #completeSignUp} — called when the user clicks the verification link and provides
 *       a password. Creates the Organization, admin IamUser, and credential, then deletes the
 *       pending record.</li>
 * </ol>
 */
public interface LocalSignUpService {

    /**
     * Initiates a new organization sign-up. Validates the request, rejects duplicates, populates
     * the server-side fields (id, token, expiresAt, created), persists the record, and sends a
     * verification email.
     *
     * @param request the user-submitted sign-up details
     * @return completes when the record is stored and the email is sent
     */
    CompletableFuture<Void> initiateSignUp(LocalPendingSignUp request);

    /**
     * Completes a pending sign-up. Validates the token, creates the Organization, admin IamUser,
     * and credential (with the hashed password), then deletes the pending record.
     *
     * @param verificationToken the token from the verification email
     * @param password          the password the user chose for their new account
     * @return the new organization's id
     */
    CompletableFuture<String> completeSignUp(String verificationToken, String password);
}
