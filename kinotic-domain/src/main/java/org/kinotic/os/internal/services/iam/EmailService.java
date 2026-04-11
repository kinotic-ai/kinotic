package org.kinotic.os.internal.services.iam;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stubbed email service for sign-up verification.
 * Currently logs the verification URL — send logic will be implemented
 * with a real email provider in a future effort.
 */
@Slf4j
@Component
public class EmailService {

    private static final String VERIFICATION_EMAIL_TEMPLATE = """
            Hi %s,

            Welcome to Kinotic OS! Please verify your email address by clicking the link below:

            %s

            This link will expire in 24 hours.

            If you did not sign up for Kinotic OS, you can safely ignore this email.

            — The Kinotic Team
            """;

    /**
     * Sends a verification email to the given address.
     * Currently stubbed — logs the verification URL instead of sending an email.
     *
     * @param email             the recipient's email address
     * @param displayName       the recipient's display name for the greeting
     * @param verificationToken the token to include in the verification URL
     */
    public void sendVerificationEmail(String email, String displayName, String verificationToken) {
        // TODO: Replace with real email send logic
        String verificationUrl = "/signup/verify?token=" + verificationToken;
        String emailBody = String.format(VERIFICATION_EMAIL_TEMPLATE, displayName, verificationUrl);

        log.info("=== VERIFICATION EMAIL (stubbed) ===");
        log.info("To: {} <{}>", displayName, email);
        log.info("Verification URL: {}", verificationUrl);
        log.info("Body:\n{}", emailBody);
        log.info("=== END VERIFICATION EMAIL ===");
    }

}
