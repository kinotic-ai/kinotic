package org.kinotic.os.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * Represents an organization sign-up from the initial submission through email verification.
 * <p>
 * The REST endpoint receives an instance with only the user-provided fields populated
 * ({@code orgName}, {@code orgDescription}, {@code email}, {@code displayName}). The
 * service then populates the server-side fields ({@code id}, {@code verificationToken},
 * {@code expiresAt}, {@code created}) and persists the record. No password is ever
 * associated with this object — the password is collected separately when the user
 * completes sign-up by clicking the verification link.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class SignUpRequest implements Identifiable<String> {

    // Server-populated fields
    private String id;
    private String verificationToken;
    private Date expiresAt;
    private Date created;

    // User-submitted fields
    private String orgName;
    private String orgDescription;
    private String email;
    private String displayName;

}
