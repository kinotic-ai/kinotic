package org.kinotic.os.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Request DTO for initiating an organization sign-up.
 * The password is in plaintext here — it is hashed before being stored in the PendingSignUp record.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class SignUpRequest {

    private String orgName;

    private String orgDescription;

    private String email;

    private String displayName;

    private String password;

}
