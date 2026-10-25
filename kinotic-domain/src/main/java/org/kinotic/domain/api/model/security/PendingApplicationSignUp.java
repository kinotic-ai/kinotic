package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * A short-lived, single-use record of a person signing up to an application with an email and a password, held
 * from the moment the verification link is emailed until the person follows it and chooses a password, when it is
 * consumed to create the application's user, then deleted.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PendingApplicationSignUp implements PendingVerification {

    private String id;

    /** Opaque single-use token carried in the verification link. */
    private String verificationToken;

    /** Hard expiry after which the token is invalid. */
    private Date expiresAt;

    private Date created;

    /** The email the person signed up with, which the user is created with. */
    private String email;

    /** The organization that owns {@link #applicationId}. */
    private String organizationId;

    /** The application the person signs up to. */
    private String applicationId;
}
