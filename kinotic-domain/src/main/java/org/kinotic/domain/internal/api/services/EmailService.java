package org.kinotic.domain.internal.api.services;

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
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sends transactional emails via Azure Communication Services. Templates are
 * rendered through Thymeleaf — the HTML body uses Spring Boot's auto-configured
 * {@link SpringTemplateEngine}; the plain-text body is rendered with an engine
 * built and owned by this service.
 * <p>
 * When {@code kinotic.email.enabled=false}, sends are skipped and the actionable
 * link is logged instead — useful for local development.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailService {

    private static final String VERIFICATION_SUBJECT = "Verify your Kinotic email";
    private static final String VERIFICATION_PATH = "/signup/verify?token=";
    private static final String INVITE_PATH = "/invite/accept?token=";

    private final KinoticDomainProperties properties;
    private final ObjectProvider<SpringTemplateEngine> htmlTemplateEngineProvider;

    private volatile EmailClient emailClient;
    private volatile SpringTemplateEngine textTemplateEngine;

    /**
     * Sends a sign-up verification email to the given address.
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
        Map<String, Object> variables = new HashMap<>();
        variables.put("displayName", displayName);
        variables.put("verificationUrl", verificationUrl);
        return sendTemplatedEmail(email, displayName, VERIFICATION_SUBJECT,
                "verification-email", variables, verificationUrl);
    }

    /**
     * Sends a member-invitation email to the given address.
     *
     * @param email            the invitee's email address
     * @param displayName      the invitee's display name for the greeting (may be blank)
     * @param inviteToken      the token to include in the acceptance URL
     * @param organizationName the name of the organization the invitee is joining
     * @return a future that completes once the send has finished (or fails if ACS rejects it)
     */
    public CompletableFuture<Void> sendInviteEmail(String email,
                                                   String displayName,
                                                   String inviteToken,
                                                   String organizationName) {
        String inviteUrl = properties.getDomain().getAppBaseUrl() + INVITE_PATH + inviteToken;
        Map<String, Object> variables = new HashMap<>();
        variables.put("displayName", displayName);
        variables.put("inviteUrl", inviteUrl);
        variables.put("organizationName", organizationName);
        return sendTemplatedEmail(email, displayName, "You've been invited to " + organizationName + " on Kinotic",
                "invite-email", variables, inviteUrl);
    }

    /**
     * Renders the named template (HTML + plain text) and sends it via ACS. When email is disabled,
     * logs {@code devLogUrl} — the actionable link — and skips the send.
     */
    private CompletableFuture<Void> sendTemplatedEmail(String email,
                                                       String displayName,
                                                       String subject,
                                                       String templateName,
                                                       Map<String, Object> variables,
                                                       String devLogUrl) {
        if (!properties.getDomain().getEmail().isEnabled()) {
            log.warn("Email sending is disabled; {} link for {} <{}>: {}",
                    templateName, displayName, email, devLogUrl);
            return CompletableFuture.completedFuture(null);
        }

        EmailClient client = getOrBuildEmailClient();
        SpringTemplateEngine htmlTemplateEngine = htmlTemplateEngineProvider.getObject();

        Context ctx = new Context();
        variables.forEach(ctx::setVariable);

        String htmlBody = htmlTemplateEngine.process("email/" + templateName, ctx);
        String textBody = getOrBuildTextTemplateEngine().process(templateName, ctx);

        EmailMessage message = new EmailMessage()
                .setSenderAddress(properties.getDomain().getEmail().getSenderAddress())
                .setToRecipients(List.of(new EmailAddress(email).setDisplayName(displayName)))
                .setSubject(subject)
                .setBodyHtml(htmlBody)
                .setBodyPlainText(textBody);

        return CompletableFuture.supplyAsync(() -> {
            SyncPoller<EmailSendResult, EmailSendResult> poller = client.beginSend(message);
            poller.waitForCompletion(properties.getDomain().getEmail().getSendTimeout());
            EmailSendResult result = poller.getFinalResult();

            if (result.getStatus() == EmailSendStatus.SUCCEEDED) {
                log.info("Sent {} email to {} (messageId={})", templateName, email, result.getId());
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
