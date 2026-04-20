package org.kinotic.os.api.services.iam;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.os.api.model.iam.SignUpRequest;

import java.util.concurrent.CompletableFuture;

/**
 * Handles organization sign-up with email verification.
 * <p>
 * Two-step flow:
 * <ol>
 *   <li>{@link #initiateSignUp} — stores the pending request and sends a verification email.
 *       No password is collected at this stage.</li>
 *   <li>{@link #completeSignUp} — called when the user clicks the verification link and
 *       provides a password. Creates the Organization, IamUser, and IamCredential, then
 *       deletes the pending request.</li>
 * </ol>
 */
@Publish
public interface SignUpService {

    /**
     * Initiates a new organization sign-up. Validates the request, checks for duplicates,
     * populates the server-side fields (id, token, expiresAt, created), persists the record,
     * and sends a verification email.
     *
     * @param request the user-submitted sign-up details
     * @return completes when the record is stored and the email is sent
     */
    CompletableFuture<Void> initiateSignUp(SignUpRequest request);

    /**
     * Completes a pending sign-up. Validates the token, creates the Organization,
     * IamUser, and IamCredential (with the hashed password), then deletes the pending record.
     *
     * @param verificationToken the token from the verification email
     * @param password          the password the user chose to set on their new account
     * @return the new organization's ID
     */
    CompletableFuture<String> completeSignUp(String verificationToken, String password);

}
