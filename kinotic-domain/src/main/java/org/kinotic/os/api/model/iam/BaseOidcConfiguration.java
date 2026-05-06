package org.kinotic.os.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * Baseline shape shared by every persisted OIDC provider configuration in the system.
 * Concrete subclasses ({@link OidcConfiguration}, {@link OrgSignupOidcConfiguration},
 * {@link SystemOidcConfiguration}) add the fields that distinguish their use case —
 * org-scope and link metadata, client secrets for confidential-client flows, etc.
 *
 * <p>Subclasses each live in their own Elasticsearch index and are looked up via a
 * dedicated CRUD service; this class is never persisted directly.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public abstract class BaseOidcConfiguration implements Identifiable<String> {

    private String id;

    /**
     * Human-readable name shown in the admin UI and on social-button labels.
     */
    private String name;

    /**
     * Provider kind selector — drives the Vert.x provider factory chosen at runtime.
     */
    private OidcProviderKind provider;

    /**
     * The OAuth 2.0 client identifier issued by the provider when Kinotic was registered
     * as an application. Sent during the authorization flow and used to validate the
     * JWT's audience claim.
     */
    private String clientId;

    /**
     * The browser-facing issuer URL. Must match the {@code iss} claim in JWTs from this provider.
     */
    private String authority;

    /**
     * Expected {@code aud} claim, or {@code null} to default to {@link #clientId}.
     */
    private String audience;

    /**
     * Disabled rows are kept in their table for audit/history but excluded from runtime
     * provider lists.
     */
    private boolean enabled;

    private Date created;

    private Date updated;
}
