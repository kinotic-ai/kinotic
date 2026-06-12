package org.kinotic.domain.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * An application's customized invitation email — at most one per application. Subject and
 * bodies are Handlebars sources rendered with the same variables as the built-in
 * invitation template; when an application has no row here, the built-in is used.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class InviteEmailTemplate implements ApplicationScoped<String> {

    private String id;

    /** The organization that owns {@link #applicationId}. */
    private String organizationId;

    /** The application whose invitation email this template customizes. Always set. */
    private String applicationId;

    /** Handlebars source for the subject line (rendered as plain text). */
    private String subject;

    /** Handlebars source for the HTML body. */
    private String htmlBody;

    /** Handlebars source for the plain-text body. */
    private String textBody;

    private Date created;

    private Date updated;
}
