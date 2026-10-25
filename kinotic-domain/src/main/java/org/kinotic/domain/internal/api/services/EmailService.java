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
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.model.EmailTemplate;
import org.kinotic.domain.api.model.InviteEmailTemplate;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.OrganizationLoginLink;
import org.kinotic.domain.api.model.security.PendingInvite;
import org.kinotic.domain.internal.api.repositories.InviteEmailTemplateRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sends transactional emails via Azure Communication Services. Bodies are rendered
 * from Handlebars templates.
 * <p>
 * When {@code kinotic.domain.email.enabled=false}, sends are skipped and the action
 * URL is logged instead, and the links a person must open are returned for the UI to show: the verification link
 * to the one signing up, an invitation's accept link to the inviter.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailService {

    private static final String VERIFICATION_SUBJECT = "Verify your Kinotic email";
    private static final String VERIFICATION_PATH = "/signup/verify?token=";
    private static final String INVITE_PATH = "/invite/accept?token=";
    private static final String ORGANIZATIONS_SUBJECT = "Your organizations on Kinotic";
    private static final String ORGANIZATION_LOGIN_PATH = "/login/";

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
    private static final Template ORGANIZATIONS_HTML = compileTemplate(HTML_TEMPLATES, "organizations-email");
    private static final Template ORGANIZATIONS_TEXT = compileTemplate(TEXT_TEMPLATES, "organizations-email");

    private final KinoticDomainProperties properties;
    private final InviteEmailTemplateRepository inviteEmailTemplateRepository;
    private final Vertx vertx;

    private volatile EmailClient emailClient;

    /**
     * Sends a verification email to the given address.
     *
     * @param email             the recipient's email address
     * @param displayName       the recipient's display name for the greeting
     * @param verificationToken the token to include in the verification URL
     * @param template          the email to send in place of Kinotic's, whose UI's verification page the link opens,
     *                          or {@code null} for Kinotic's, which links to the portal's
     * @return a future emitting the verification link when email is disabled, for the UI to open in place of the
     *         email, or {@code null} once the email is sent (failing if ACS rejects it)
     */
    public Future<String> sendVerificationEmail(String email,
                                              String displayName,
                                              String verificationToken,
                                              EmailTemplate template) {
        String verificationUrl = uiBaseUrl(template) + VERIFICATION_PATH + verificationToken;

        if (!properties.getDomain().getEmail().isEnabled()) {
            log.warn("Email sending is disabled; verification URL for {} <{}>: {}",
                    displayName, email, verificationUrl);
            return Future.succeededFuture(verificationUrl);
        }

        Map<String, Object> variables = Map.of("displayName", displayName,
                                               "verificationUrl", verificationUrl);

        Future<Void> sent;
        if (template != null) {
            sent = sendInline(email, displayName, template.getSubject(), template.getHtmlBody(), template.getTextBody(),
                              variables);
        } else {
            sent = send(email,
                        displayName,
                        VERIFICATION_SUBJECT,
                        render(VERIFICATION_HTML, variables),
                        render(VERIFICATION_TEXT, variables));
        }
        return sent.map((String) null);
    }

    /**
     * Sends an invitation email carrying the accept link for the given invite. An
     * application invite is rendered with the application's {@link InviteEmailTemplate}
     * when one exists; any other is rendered with {@code template}, or with Kinotic's own when it is {@code null}.
     *
     * @param invite           the saved invitation (token, recipient, scope, inviter attribution)
     * @param organizationName display name of the inviting organization
     * @param template         the email to send in place of Kinotic's, whose UI's invitation page the link opens, or
     *                         {@code null} for Kinotic's, which links to the portal's
     * @return a future that completes once the send has finished (or fails if ACS rejects it)
     */
    public Future<Void> sendInviteEmail(PendingInvite invite, String organizationName, EmailTemplate template) {
        String acceptUrl = uiBaseUrl(template) + INVITE_PATH + invite.getVerificationToken();
        String toName = invite.getDisplayName() != null ? invite.getDisplayName() : invite.getEmail();

        if (!properties.getDomain().getEmail().isEnabled()) {
            log.warn("Email sending is disabled; invite accept URL for {} <{}>: {}",
                    toName, invite.getEmail(), acceptUrl);
            return Future.succeededFuture();
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

        Future<InviteEmailTemplate> applicationTemplate = invite.getApplicationId() != null
                ? inviteEmailTemplateRepository.findByApplication(invite.getApplicationId(), invite.getOrganizationId())
                : Future.succeededFuture();
        return applicationTemplate.compose(own -> {
            Future<Void> ret;
            if (own != null) {
                ret = sendInline(invite.getEmail(), toName, own.getSubject(), own.getHtmlBody(), own.getTextBody(),
                                 variables);
            } else if (template != null) {
                ret = sendInline(invite.getEmail(), toName, template.getSubject(), template.getHtmlBody(),
                                 template.getTextBody(), variables);
            } else {
                String subject = invite.getApplicationId() != null
                        ? "You're invited to join " + invite.getApplicationId()
                        : "You're invited to join " + organizationName + " on Kinotic";
                ret = send(invite.getEmail(), toName, subject, render(INVITE_HTML, variables), render(INVITE_TEXT, variables));
            }
            return ret;
        });
    }

    /**
     * Sends the person at {@code email} the list of organizations they are a member of, each with a link to
     * that organization's login. An empty list sends an email saying the address belongs to no
     * organization, so the request that asked for it reveals nothing about the address.
     *
     * @param email         the recipient's email address
     * @param organizations the organizations the address is a member of, possibly none
     * @return a future emitting the organizations' login links when email is disabled, for the UI to show in place
     *         of the email, or {@code null} once the email is sent (failing if ACS rejects it)
     */
    public Future<List<OrganizationLoginLink>> sendOrganizationsEmail(String email, List<Organization> organizations) {
        List<OrganizationLoginLink> links = organizations.stream()
                .map(organization -> new OrganizationLoginLink(organization.getName(),
                                                               portalBaseUrl() + ORGANIZATION_LOGIN_PATH + organization.getId()))
                .toList();

        if (!properties.getDomain().getEmail().isEnabled()) {
            log.warn("Email sending is disabled; organizations of <{}>: {}", email,
                     links.stream().map(OrganizationLoginLink::loginUrl).toList());
            return Future.succeededFuture(links);
        }

        // HashMap rather than Map.of: an empty list is omitted so the templates' {{#if organizations}} sees it as falsy
        Map<String, Object> variables = new HashMap<>();
        if (!links.isEmpty()) {
            variables.put("organizations", links.stream()
                                                .map(link -> Map.<String, Object>of("name", link.name(), "loginUrl", link.loginUrl()))
                                                .toList());
        }
        return send(email,
                    email,
                    ORGANIZATIONS_SUBJECT,
                    render(ORGANIZATIONS_HTML, variables),
                    render(ORGANIZATIONS_TEXT, variables))
                .map((List<OrganizationLoginLink>) null);
    }

    /**
     * The accept link of {@code invite} for the UI to show the inviter when email is disabled. Anyone holding
     * the link can accept the invitation as the invited address.
     *
     * @return the link Kinotic's invitation email carries, which opens the portal's invitation page
     */
    public String inviteAcceptUrl(PendingInvite invite) {
        return portalBaseUrl() + INVITE_PATH + invite.getVerificationToken();
    }

    private String uiBaseUrl(EmailTemplate template) {
        return template != null ? template.getUiBaseUrl() : portalBaseUrl();
    }

    // checked per send: a server that sends no mail leaves it unset
    private String portalBaseUrl() {
        String ret = properties.getDomain().getPortalBaseUrl();
        Validate.notBlank(ret, "kinotic.domain.portalBaseUrl must be set on a server that sends email");
        return ret;
    }

    /**
     * Checks an {@link EmailTemplate} a caller passes, so a broken one fails the call before it saves anything.
     *
     * @param template the template, or {@code null} for Kinotic's own, which needs no check
     * @throws IllegalArgumentException when the template is not valid, as {@link EmailTemplate} describes
     */
    public void validateTemplate(EmailTemplate template) {
        if (template != null) {
            Validate.isTrue(isBaseUrl(template.getUiBaseUrl()),
                            "The email template's uiBaseUrl must be an absolute http or https URL naming a host, with no"
                                    + " user info, query, fragment or trailing slash");
            validateTemplateSources(template.getSubject(), template.getHtmlBody(), template.getTextBody());
        }
    }

    /**
     * Parses and test-renders email template sources, so a broken template fails when it is saved or passed rather
     * than when the next email is sent.
     *
     * @throws IllegalArgumentException when a source is blank, or carrying the Handlebars message when one does not
     *                                  compile or render
     */
    public void validateTemplateSources(String subject, String htmlBody, String textBody) {
        Validate.isTrue(StringUtils.hasText(subject), "An email template's subject is required");
        Validate.isTrue(StringUtils.hasText(htmlBody), "An email template's htmlBody is required");
        Validate.isTrue(StringUtils.hasText(textBody), "An email template's textBody is required");
        Map<String, Object> sample = new HashMap<>();
        sample.put("inviterName", "Inviter Name");
        sample.put("organizationName", "Organization Name");
        sample.put("applicationName", "application-id");
        sample.put("acceptUrl", "https://example.invalid/invite/accept?token=sample");
        sample.put("expiresInDays", 7L);
        sample.put("displayName", "Display Name");
        sample.put("verificationUrl", "https://example.invalid/signup/verify?token=sample");
        try {
            renderInline(TEXT_TEMPLATES, subject, sample);
            renderInline(HTML_TEMPLATES, htmlBody, sample);
            renderInline(TEXT_TEMPLATES, textBody, sample);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalArgumentException("Invalid email template: " + cause.getMessage(), e);
        }
    }

    // the base the link paths are appended to, so a query, a fragment or a trailing slash would break every link, and
    // user info would show a link to one host that opens another (https://trusted.example@attacker.example)
    private static boolean isBaseUrl(String url) {
        boolean ret = false;
        if (url != null && !url.endsWith("/")) {
            try {
                URI uri = new URI(url);
                ret = ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        && uri.getHost() != null
                        && uri.getRawUserInfo() == null
                        && uri.getRawQuery() == null
                        && uri.getRawFragment() == null;
            } catch (URISyntaxException e) {
                // an unparseable URL is not a base URL
            }
        }
        return ret;
    }

    /**
     * Renders a template source a caller supplied, stored or passed in, with the same map-only resolution
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

    private Future<Void> sendInline(String toEmail,
                                    String toName,
                                    String subjectSource,
                                    String htmlSource,
                                    String textSource,
                                    Map<String, Object> variables) {
        return send(toEmail,
                    toName,
                    renderInline(TEXT_TEMPLATES, subjectSource, variables),
                    renderInline(HTML_TEMPLATES, htmlSource, variables),
                    renderInline(TEXT_TEMPLATES, textSource, variables));
    }

    private Future<Void> send(String toEmail,
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

        return Future.fromCompletionStage(CompletableFuture.supplyAsync(() -> {
            SyncPoller<EmailSendResult, EmailSendResult> poller = client.beginSend(message);
            poller.waitForCompletion(properties.getDomain().getEmail().getSendTimeout());
            EmailSendResult result = poller.getFinalResult();

            if (result.getStatus() == EmailSendStatus.SUCCEEDED) {
                log.info("Sent '{}' email to {} (messageId={})", subject, toEmail, result.getId());
                return null;
            }
            throw new IllegalStateException("Azure Communication Services rejected the send: status="
                    + result.getStatus() + " messageId=" + result.getId());
        }), vertx.getOrCreateContext());
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
