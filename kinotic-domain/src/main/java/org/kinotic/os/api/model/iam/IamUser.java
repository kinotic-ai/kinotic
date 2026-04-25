package org.kinotic.os.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * Represents an authenticated identity at any scope layer in the IAM system.
 * Each user is scoped to exactly one layer and is unique by email within that scope.
 * Users must be pre-created by an administrator before they can authenticate.
 * <p>
 * The {@code authScopeType} is a plain String to keep the scope system extensible —
 * customers using kinotic-core for RPC without the full IAM stack can define their own scope types.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class IamUser implements Identifiable<String> {

    private String id;

    /**
     * Email address, unique within the user's scope (authScopeType + authScopeId).
     */
    private String email;

    private String displayName;

    /**
     * Authentication method: {@link AuthType#LOCAL} for email/password or {@link AuthType#OIDC} for federated identity.
     */
    private AuthType authType;

    /**
     * The {@code sub} claim from the OIDC token. Populated automatically on first successful OIDC login
     * when the user was pre-created by an administrator with only an email.
     */
    private String oidcSubject;

    /**
     * Reference to the {@link OidcConfiguration} used for authentication. Populated alongside oidcSubject on first OIDC login.
     */
    private String oidcConfigId;

    /**
     * The scope layer this user belongs to. Well-known values are "SYSTEM", "ORGANIZATION",
     * and "APPLICATION", but custom values are allowed for extensibility.
     */
    private String authScopeType;

    /**
     * The scope identifier: "kinotic" for SYSTEM, the organization ID for ORGANIZATION,
     * or the application ID for APPLICATION. Always set — never null.
     */
    private String authScopeId;

    /**
     * The client tenant this user belongs to within their application's data model.
     * Only meaningful when {@link #authScopeType} is {@code "APPLICATION"} — application
     * tenants partition the data of {@code MultiTenancyType.SHARED} entities owned by the app.
     * Must be null for SYSTEM and ORGANIZATION scopes; those identities are not tenants.
     */
    private String tenantId;

    private boolean enabled;

    private Date created;

    private Date updated;

}
