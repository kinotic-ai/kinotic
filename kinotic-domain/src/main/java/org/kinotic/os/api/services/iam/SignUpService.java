package org.kinotic.os.api.services.iam;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.os.api.model.iam.SignUpRequest;

import java.util.concurrent.CompletableFuture;

/**
 * Handles organization sign-up with email verification.
 * The sign-up is a two-step process: initiate (stores pending record, sends verification email)
 * and verify (creates the Organization and IamUser after email confirmation).
 */
@Publish
public interface SignUpService {

    /**
     * Initiates a new organization sign-up. Validates the request, checks for duplicates,
     * stores a pending sign-up record, and sends a verification email.
     * The Organization and IamUser are NOT created until {@link #verifySignUp} is called.
     *
     * @param request the sign-up details (org name, email, password, etc.)
     * @return completes when the pending record is stored and the email is sent
     */
    CompletableFuture<Void> initiateSignUp(SignUpRequest request);

    /**
     * Completes a pending sign-up by verifying the email token. Creates the Organization,
     * IamUser (scoped to the new org), and IamCredential, then deletes the pending record.
     *
     * @param verificationToken the token from the verification email
     * @return the new organization's ID
     */
    CompletableFuture<String> verifySignUp(String verificationToken);

}
