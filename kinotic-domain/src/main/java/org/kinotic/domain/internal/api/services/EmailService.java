package org.kinotic.domain.internal.api.services;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailAddress;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.communication.email.models.EmailSendStatus;
import com.azure.core.util.polling.SyncPoller;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.hubspot.jinjava.Jinjava;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sends transactional emails via Azure Communication Services. Bodies are rendered
 * from Jinja-style templates (jinjava).
 * <p>
 * When {@code kinotic.email.enabled=false}, sends are skipped and the action
 * URL is logged instead — useful for local development.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailService {

    private static final String VERIFICATION_SUBJECT = "Verify your Kinotic email";
    private static final String VERIFICATION_PATH = "/signup/verify?token=";

    private static final String VERIFICATION_HTML = loadTemplate("templates/email/verification-email.html");
    private static final String VERIFICATION_TEXT = loadTemplate("templates/email/verification-email.txt");

    // Thread-safe and intended to be created once and shared across renders.
    private static final Jinjava JINJAVA = new Jinjava();

    private final KinoticDomainProperties properties;

    private volatile EmailClient emailClient;

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
        String verificationUrl = properties.getDomain().getAppBaseUrl() + VERIFICATION_PATH + verificationToken;

        if (!properties.getDomain().getEmail().isEnabled()) {
            log.warn("Email sending is disabled; verification URL for {} <{}>: {}",
                    displayName, email, verificationUrl);
            return CompletableFuture.completedFuture(null);
        }

        Map<String, Object> variables = Map.of("displayName", displayName,
                                               "verificationUrl", verificationUrl);

        return send(email,
                    displayName,
                    VERIFICATION_SUBJECT,
                    render(VERIFICATION_HTML, variables),
                    render(VERIFICATION_TEXT, variables));
    }

    private String render(String templateSource, Map<String, Object> variables) {
        return JINJAVA.render(templateSource, variables);
    }

    private CompletableFuture<Void> send(String toEmail,
                                         String toName,
                                         String subject,
                                         String htmlBody,
                                         String textBody) {
        EmailClient client = getOrBuildEmailClient();

        EmailMessage message = new EmailMessage()
                .setSenderAddress(properties.getDomain().getEmail().getSenderAddress())
                .setToRecipients(List.of(new EmailAddress(toEmail).setDisplayName(toName)))
                .setSubject(subject)
                .setBodyHtml(htmlBody)
                .setBodyPlainText(textBody);

        return CompletableFuture.supplyAsync(() -> {
            SyncPoller<EmailSendResult, EmailSendResult> poller = client.beginSend(message);
            poller.waitForCompletion(properties.getDomain().getEmail().getSendTimeout());
            EmailSendResult result = poller.getFinalResult();

            if (result.getStatus() == EmailSendStatus.SUCCEEDED) {
                log.info("Sent '{}' email to {} (messageId={})", subject, toEmail, result.getId());
                return null;
            }
            throw new IllegalStateException("Azure Communication Services rejected the send: status="
                    + result.getStatus() + " messageId=" + result.getId());
        });
    }

    /**
     * Lazily builds the ACS {@link EmailClient} on first successful send. Auth uses
     * {@code DefaultAzureCredential} — on AKS this resolves to a Workload-Identity
     * federated token; on a developer laptop it falls through to {@code AzureCliCredential}
     * (via {@code az login}). No connection strings or secrets are read from config.
     */
    private EmailClient getOrBuildEmailClient() {
        EmailClient client = emailClient;
        if (client == null) {
            synchronized (this) {
                client = emailClient;
                if (client == null) {
                    DefaultAzureCredentialBuilder credBuilder = new DefaultAzureCredentialBuilder();
                    if (StringUtils.hasText(properties.getDomain().getEmail().getManagedIdentityClientId())) {
                        credBuilder.managedIdentityClientId(properties.getDomain().getEmail().getManagedIdentityClientId());
                    }
                    client = new EmailClientBuilder()
                            .endpoint(properties.getDomain().getEmail().getEndpoint())
                            .credential(credBuilder.build())
                            .buildClient();
                    emailClient = client;
                }
            }
        }
        return client;
    }

    /**
     * Reads a built-in template off the classpath at class initialization, so a missing
     * resource fails fast at startup rather than on the first send.
     */
    private static String loadTemplate(String resourcePath) {
        try (InputStream in = EmailService.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing email template on classpath: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed reading email template: " + resourcePath, e);
        }
    }

}
