package org.kinotic.os.internal.api.services;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.models.EmailAddress;
import com.azure.communication.email.models.EmailAttachment;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.communication.email.models.EmailSendStatus;
import com.azure.core.util.BinaryData;
import com.azure.core.util.polling.SyncPoller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.api.config.KinoticDomainProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Sends transactional emails via Azure Communication Services. Templates are
 * rendered through Thymeleaf — HTML via the auto-configured {@link SpringTemplateEngine}
 * and plain text via the {@code emailTextTemplateEngine} bean configured in
 * {@code EmailConfiguration}.
 * <p>
 * When {@code kinotic.email.enabled=false} (or no {@link EmailClient} bean is
 * present), sends are skipped and the verification URL is logged instead — useful
 * for local development.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailService {

    private static final String LOGO_RESOURCE = "templates/email/assets/kinotic-wordmark.png";
    private static final String LOGO_CONTENT_ID = "kinotic-wordmark";
    private static final String VERIFICATION_SUBJECT = "Verify your Kinotic email";
    private static final String VERIFICATION_PATH = "/signup/verify?token=";

    private final KinoticDomainProperties properties;
    private final ObjectProvider<EmailClient> emailClientProvider;
    private final ObjectProvider<SpringTemplateEngine> htmlTemplateEngineProvider;

    @Qualifier("emailTextTemplateEngine")
    private final ObjectProvider<SpringTemplateEngine> textTemplateEngineProvider;

    private volatile byte[] logoBytes;

    /**
     * Sends a verification email to the given address.
     *
     * @param email             the recipient's email address
     * @param displayName       the recipient's display name for the greeting
     * @param verificationToken the token to include in the verification URL
     * @return a future that completes once the send has finished (or fails if ACS rejects it)
     */
    public CompletableFuture<Void> sendVerificationEmail(String email,
                                                         String displayName,
                                                         String verificationToken) {
        String verificationUrl = properties.getEmail().getAppBaseUrl() + VERIFICATION_PATH + verificationToken;

        if (!properties.getEmail().isEnabled()) {
            log.warn("Email sending is disabled; verification URL for {} <{}>: {}",
                    displayName, email, verificationUrl);
            return CompletableFuture.completedFuture(null);
        }

        EmailClient emailClient = emailClientProvider.getObject();
        SpringTemplateEngine htmlTemplateEngine = htmlTemplateEngineProvider.getObject();
        SpringTemplateEngine textTemplateEngine = textTemplateEngineProvider.getObject();

        Context ctx = new Context();
        ctx.setVariable("displayName", displayName);
        ctx.setVariable("verificationUrl", verificationUrl);

        String htmlBody = htmlTemplateEngine.process("email/verification-email", ctx);
        String textBody = textTemplateEngine.process("verification-email", ctx);

        EmailAttachment logoAttachment = new EmailAttachment(
                "kinotic-wordmark.png",
                "image/png",
                BinaryData.fromBytes(loadLogoBytes()))
                .setContentId(LOGO_CONTENT_ID);

        EmailMessage message = new EmailMessage()
                .setSenderAddress(properties.getEmail().getSenderAddress())
                .setToRecipients(List.of(new EmailAddress(email).setDisplayName(displayName)))
                .setSubject(VERIFICATION_SUBJECT)
                .setBodyHtml(htmlBody)
                .setBodyPlainText(textBody)
                .setAttachments(List.of(logoAttachment));

        return CompletableFuture.supplyAsync(() -> {
            SyncPoller<EmailSendResult, EmailSendResult> poller = emailClient.beginSend(message);
            poller.waitForCompletion(properties.getEmail().getSendTimeout());
            EmailSendResult result = poller.getFinalResult();

            if (result.getStatus() == EmailSendStatus.SUCCEEDED) {
                log.info("Sent verification email to {} (messageId={})", email, result.getId());
                return null;
            }
            throw new IllegalStateException("Azure Communication Services rejected the send: status="
                    + result.getStatus() + " messageId=" + result.getId());
        });
    }

    private byte[] loadLogoBytes() {
        byte[] bytes = logoBytes;
        if (bytes == null) {
            synchronized (this) {
                bytes = logoBytes;
                if (bytes == null) {
                    try (var in = new ClassPathResource(LOGO_RESOURCE).getInputStream()) {
                        bytes = StreamUtils.copyToByteArray(in);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Unable to load email logo asset: " + LOGO_RESOURCE, e);
                    }
                    logoBytes = bytes;
                }
            }
        }
        return bytes;
    }

}
