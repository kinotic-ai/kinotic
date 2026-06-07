package org.kinotic.os.api.services.iam;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * The authentication methods an administrator may choose when inviting a member into a scope.
 * LOCAL (email/password) is always available; OIDC is available only when the scope has an
 * enabled OIDC configuration, in which case {@link #oidcProviderName} carries its display name.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class InviteOptions {

    /** Whether the invitee may set a password (always true). */
    private boolean localEnabled;

    /** Whether the invitee may sign in through the scope's OIDC provider. */
    private boolean oidcEnabled;

    /** Display name of the scope's OIDC provider, or null when {@link #oidcEnabled} is false. */
    private String oidcProviderName;
}
