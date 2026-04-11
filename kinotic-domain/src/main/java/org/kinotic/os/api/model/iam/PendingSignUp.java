package org.kinotic.os.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * Temporary record stored in Elasticsearch while awaiting email verification during org sign-up.
 * Once the user verifies their email, the pending sign-up is converted into an Organization,
 * IamUser, and IamCredential, and this record is deleted.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PendingSignUp implements Identifiable<String> {

    private String id;

    private String orgName;

    private String orgDescription;

    private String email;

    private String displayName;

    /**
     * bcrypt hash of the password provided during sign-up. Stored here so the user
     * doesn't need to re-enter it after verification.
     */
    private String passwordHash;

    /**
     * UUID token sent in the verification email. Used to look up this record when
     * the user clicks the verification link.
     */
    private String verificationToken;

    /**
     * Timestamp after which this pending sign-up is considered expired and should be rejected.
     */
    private Date expiresAt;

    private Date created;

}
