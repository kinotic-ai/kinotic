package org.kinotic.domain.internal.api.services;

import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.EmailTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers {@link EmailService#validateTemplate}: which templates a caller may pass.
 */
class EmailServiceTest {

    private final EmailService emailService = new EmailService(null, null, null);

    @Test
    void aCompleteTemplateIsAccepted() {
        assertDoesNotThrow(() -> emailService.validateTemplate(template("https://builder.example.com")));
        assertDoesNotThrow(() -> emailService.validateTemplate(template("HTTPS://builder.example.com/app")));
        assertDoesNotThrow(() -> emailService.validateTemplate(null));
    }

    @Test
    void aUiBaseUrlLinksCannotBeAppendedToIsRejected() {
        for (String url : new String[]{null, "", "https://builder.example.com/", "https://builder.example.com?x=1",
                                       "https://builder.example.com#top", "builder.example.com", "ftp://builder.example.com",
                                       " https://builder.example.com", "https://builder.example.com@attacker.example",
                                       "https://builder.example.com:abc"}) {
            assertThrows(IllegalArgumentException.class, () -> emailService.validateTemplate(template(url)), String.valueOf(url));
        }
    }

    @Test
    void aBlankOrBrokenSourceIsRejected() {
        assertThrows(IllegalArgumentException.class,
                     () -> emailService.validateTemplate(template("https://builder.example.com").setSubject(" ")));
        assertThrows(IllegalArgumentException.class,
                     () -> emailService.validateTemplate(template("https://builder.example.com").setTextBody(null)));
        assertThrows(IllegalArgumentException.class,
                     () -> emailService.validateTemplate(template("https://builder.example.com").setHtmlBody("{{#if acceptUrl}}")));
        assertThrows(IllegalArgumentException.class,
                     () -> emailService.validateTemplate(template("https://builder.example.com").setHtmlBody("{{> header}}")));
    }

    @Test
    void aTemplateThatCouldRepeatABodyIsRejected() {
        for (String body : new String[]{"{{#each this}}{{#each this}}{{this}}{{/each}}{{/each}}",
                                        "{{#*inline \"p\"}}{{acceptUrl}}{{/inline}}{{> p}}{{> p}}",
                                        "{{#partial \"p\"}}{{acceptUrl}}{{/partial}}{{#block \"p\"}}{{/block}}",
                                        "{{#if acceptUrl}}{{> p}}{{/if}}"}) {
            assertThrows(IllegalArgumentException.class,
                         () -> emailService.validateTemplate(template("https://builder.example.com").setHtmlBody(body)), body);
        }
    }

    @Test
    void aConditionalSectionIsAccepted() {
        assertDoesNotThrow(() -> emailService.validateTemplate(
                template("https://builder.example.com").setTextBody("{{#if inviterName}}{{inviterName}} invited you{{else}}You are invited{{/if}}: {{acceptUrl}}")));
    }

    private static EmailTemplate template(String uiBaseUrl) {
        return new EmailTemplate().setUiBaseUrl(uiBaseUrl)
                                  .setSubject("Join {{organizationName}}")
                                  .setHtmlBody("<a href=\"{{{acceptUrl}}}\">Accept</a>")
                                  .setTextBody("Accept: {{acceptUrl}}");
    }
}
