package org.kinotic.domain.internal.api.services;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailAddress;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.communication.email.models.EmailSendStatus;
import com.azure.core.util.polling.SyncPoller;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.context.MapValueResolver;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.model.InviteEmailTemplate;
import org.kinotic.domain.api.model.security.PendingInvite;
import org.kinotic.domain.internal.api.repositories.InviteEmailTemplateRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sends transactional emails via Azure Communication Services. Bodies are rendered
 * from Handlebars templates.
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
    private static final String INVITE_PATH = "/invite/accept?token=";

    // Two engines differing by template suffix and escaping: HTML bodies HTML-escape
    // {{var}} by default (templates opt out with {{{var}}} for system-constructed URLs);
    // plain-text bodies must not be escaped at all.
    private static final Handlebars HTML_TEMPLATES =
            new Handlebars(new ClassPathTemplateLoader("/templates/email", ".html"));
    private static final Handlebars TEXT_TEMPLATES =
            new Handlebars(new ClassPathTemplateLoader("/templates/email", ".txt"))
                    .with(EscapingStrategy.NOOP);

    private static final Template VERIFICATION_HTML = compileTemplate(HTML_TEMPLATES, "verification-email");
    private static final Template VERIFICATION_TEXT = compileTemplate(TEXT_TEMPLATES, "verification-email");
    private static final Template INVITE_HTML = compileTemplate(HTML_TEMPLATES, "invite-email");
    private static final Template INVITE_TEXT = compileTemplate(TEXT_TEMPLATES, "invite-email");

    private final KinoticDomainProperties properties;
    private final InviteEmailTemplateRepository inviteEmailTemplateRepository;

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

    /**
     * Sends an invitation email carrying the accept link for the given invite. An
     * application invite is rendered with the application's {@link InviteEmailTemplate}
     * when one exists; org invites and apps without a template use the built-in template.
     *
     * @param invite           the saved invitation (token, recipient, scope, inviter attribution)
     * @param organizationName display name of the inviting organization
     * @return a future that completes once the send has finished (or fails if ACS rejects it)
     */
    public CompletableFuture<Void> sendInviteEmail(PendingInvite invite, String organizationName) {
        String acceptUrl = properties.getDomain().getAppBaseUrl() + INVITE_PATH + invite.getVerificationToken();
        String toName = invite.getDisplayName() != null ? invite.getDisplayName() : invite.getEmail();

        if (!properties.getDomain().getEmail().isEnabled()) {
            log.warn("Email sending is disabled; invite accept URL for {} <{}>: {}",
                    toName, invite.getEmail(), acceptUrl);
            return CompletableFuture.completedFuture(null);
        }

        long expiresInDays = Math.max(1, Math.round(
                (invite.getExpiresAt().getTime() - System.currentTimeMillis()) / 86_400_000.0));

        // HashMap rather than Map.of: applicationName is omitted (not null) for org invites
        // so the templates' {{#if applicationName}} branch sees it as falsy.
        Map<String, Object> variables = new HashMap<>();
        variables.put("inviterName", invite.getInvitedByName());
        variables.put("organizationName", organizationName);
        if (invite.getApplicationId() != null) {
            variables.put("applicationName", invite.getApplicationId());
        }
        variables.put("acceptUrl", acceptUrl);
        variables.put("expiresInDays", expiresInDays);

        String defaultSubject = invite.getApplicationId() != null
                ? "You're invited to join " + invite.getApplicationId()
                : "You're invited to join " + organizationName + " on Kinotic";

        if (invite.getApplicationId() == null) {
            return send(invite.getEmail(), toName, defaultSubject,
                        render(INVITE_HTML, variables),
                        render(INVITE_TEXT, variables));
        }
        return inviteEmailTemplateRepository.findByApplication(invite.getApplicationId(),
                                                               invite.getOrganizationId())
                .thenCompose(template -> {
                    if (template == null) {
                        return send(invite.getEmail(), toName, defaultSubject,
                                    render(INVITE_HTML, variables),
                                    render(INVITE_TEXT, variables));
                    }
                    return send(invite.getEmail(), toName,
                                renderInline(TEXT_TEMPLATES, template.getSubject(), variables),
                                renderInline(HTML_TEMPLATES, template.getHtmlBody(), variables),
                                renderInline(TEXT_TEMPLATES, template.getTextBody(), variables));
                });
    }

    /**
     * Parses and test-renders invitation template sources, so a broken template fails at
     * save time rather than when the next invitation is sent.
     *
     * @throws IllegalArgumentException carrying the Handlebars message when a source does
     *                                  not compile or render
     */
    public void validateInviteTemplate(String subject, String htmlBody, String textBody) {
        Map<String, Object> sample = new HashMap<>();
        sample.put("inviterName", "Inviter Name");
        sample.put("organizationName", "Organization Name");
        sample.put("applicationName", "application-id");
        sample.put("acceptUrl", "https://example.invalid/invite/accept?token=sample");
        sample.put("expiresInDays", 7L);
        try {
            renderInline(TEXT_TEMPLATES, subject, sample);
            renderInline(HTML_TEMPLATES, htmlBody, sample);
            renderInline(TEXT_TEMPLATES, textBody, sample);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalArgumentException("Invalid email template: " + cause.getMessage(), e);
        }
    }

    /**
     * Renders a stored (user-authored) template source with the same map-only resolution
     * as the built-ins. Compiled per send — stored templates can change between sends.
     */
    private static String renderInline(Handlebars engine, String source, Map<String, Object> variables) {
        try {
            return render(engine.compileInline(source), variables);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed compiling stored email template", e);
        }
    }

    private static String render(Template template, Map<String, Object> variables) {
        // Map-only resolution: no reflective bean/method traversal of context objects,
        // which is the known handlebars.java template-injection vector.
        Context context = Context.newBuilder(variables)
                                 .resolver(MapValueResolver.INSTANCE)
                                 .build();
        try {
            return template.apply(context);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed rendering email template", e);
        } finally {
            context.destroy();
        }
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
     * Compiles a built-in template at class initialization, so a missing or broken
     * template fails fast at startup rather than on the first send.
     */
    private static Template compileTemplate(Handlebars handlebars, String name) {
        try {
            return handlebars.compile(name);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed compiling email template: " + name, e);
        }
    }

}
