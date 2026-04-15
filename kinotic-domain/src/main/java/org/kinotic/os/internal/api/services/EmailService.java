package org.kinotic.os.internal.api.services;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailAddress;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.communication.email.models.EmailSendStatus;
import com.azure.core.util.polling.SyncPoller;
import com.azure.identity.DefaultAzureCredentialBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.api.config.KinoticDomainProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Sends transactional emails via Azure Communication Services. Templates are
 * rendered through Thymeleaf — the HTML body uses Spring Boot's auto-configured
 * {@link SpringTemplateEngine}; the plain-text body is rendered with an engine
 * built and owned by this service.
 * <p>
 * When {@code kinotic.email.enabled=false}, sends are skipped and the verification
 * URL is logged instead — useful for local development.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailService {

    private static final String VERIFICATION_SUBJECT = "Verify your Kinotic email";
    private static final String VERIFICATION_PATH = "/signup/verify?token=";

    private final KinoticDomainProperties properties;
    private final ObjectProvider<SpringTemplateEngine> htmlTemplateEngineProvider;

    private volatile EmailClient emailClient;
    private volatile SpringTemplateEngine textTemplateEngine;

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
        String verificationUrl = properties.getAppBaseUrl() + VERIFICATION_PATH + verificationToken;

        if (!properties.getEmail().isEnabled()) {
            log.warn("Email sending is disabled; verification URL for {} <{}>: {}",
                    displayName, email, verificationUrl);
            return CompletableFuture.completedFuture(null);
        }

        EmailClient client = getOrBuildEmailClient();
        SpringTemplateEngine htmlTemplateEngine = htmlTemplateEngineProvider.getObject();

        Context ctx = new Context();
        ctx.setVariable("displayName", displayName);
        ctx.setVariable("verificationUrl", verificationUrl);

        String htmlBody = htmlTemplateEngine.process("email/verification-email", ctx);
        String textBody = getOrBuildTextTemplateEngine().process("verification-email", ctx);

        EmailMessage message = new EmailMessage()
                .setSenderAddress(properties.getEmail().getSenderAddress())
                .setToRecipients(List.of(new EmailAddress(email).setDisplayName(displayName)))
                .setSubject(VERIFICATION_SUBJECT)
                .setBodyHtml(htmlBody)
                .setBodyPlainText(textBody);

        return CompletableFuture.supplyAsync(() -> {
            SyncPoller<EmailSendResult, EmailSendResult> poller = client.beginSend(message);
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
                    if (StringUtils.hasText(properties.getEmail().getManagedIdentityClientId())) {
                        credBuilder.managedIdentityClientId(properties.getEmail().getManagedIdentityClientId());
                    }
                    client = new EmailClientBuilder()
                            .endpoint(properties.getEmail().getEndpoint())
                            .credential(credBuilder.build())
                            .buildClient();
                    emailClient = client;
                }
            }
        }
        return client;
    }

    /**
     * Lazily builds a Thymeleaf engine in {@link TemplateMode#TEXT} mode for the
     * plain-text email body. The HTML body uses Spring Boot's auto-configured engine.
     */
    private SpringTemplateEngine getOrBuildTextTemplateEngine() {
        SpringTemplateEngine engine = textTemplateEngine;
        if (engine == null) {
            synchronized (this) {
                engine = textTemplateEngine;
                if (engine == null) {
                    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
                    resolver.setPrefix("templates/email/");
                    resolver.setSuffix(".txt");
                    resolver.setTemplateMode(TemplateMode.TEXT);
                    resolver.setCharacterEncoding("UTF-8");
                    resolver.setCacheable(true);

                    engine = new SpringTemplateEngine();
                    engine.setTemplateResolver(resolver);
                    textTemplateEngine = engine;
                }
            }
        }
        return engine;
    }

}
