package org.kinotic.domain.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * Represents an authenticated identity at any scope layer in the IAM system.
 * Each user is scoped to exactly one layer — SYSTEM, an Organization, or an Application
 * within an Organization — and is unique by email within that scope. The scope is encoded
 * structurally by which of {@link #organizationId} / {@link #applicationId} is set:
 * <ul>
 *   <li>both null → SYSTEM (platform operators)</li>
 *   <li>{@code organizationId} set, {@code applicationId} null → ORGANIZATION</li>
 *   <li>both set → APPLICATION (a user of an Application owned by an Organization)</li>
 * </ul>
 * Users must be pre-created by an administrator before they can authenticate.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ParticipantIdentity implements Identifiable<String> {

    private String id;

    /**
     * Email address, unique within the user's scope (organizationId + applicationId).
     */
    private String email;

    private String displayName;

    /**
     * Authentication method: {@link AuthType#LOCAL} for email/password or {@link AuthType#OIDC} for federated identity.
     */
    private AuthType authType;

    /**
     * The {@code sub} claim from the OIDC token. Populated when an OIDC user is provisioned
     * (auto on first login, or via pending sign-up completion); used together with
     * {@link #oidcConfigId} to resolve the ParticipantIdentity back from the IdP callback.
     */
    private String oidcSubject;

    /**
     * Reference to the OIDC configuration used to provision this user. Distinguishes
     * {@code sub}s issued by different IdPs that might collide.
     */
    private String oidcConfigId;

    /**
     * The Organization this user belongs to. {@code null} for SYSTEM users; set for both
     * ORGANIZATION and APPLICATION users (an Application is owned by exactly one Org, so
     * APP-scope users intrinsically carry the owning org's id).
     */
    private String organizationId;

    /**
     * The Application this user authenticates against. {@code null} for SYSTEM and
     * ORGANIZATION users; set for APPLICATION users. When set, {@link #organizationId}
     * must also be set (the org that owns the application).
     */
    private String applicationId;

    /**
     * The client tenant this user belongs to within their application's data model.
     * Only meaningful when {@link #applicationId} is set — application tenants partition
     * the data of {@code MultiTenancyType.SHARED} entities owned by the app. Must be null
     * for SYSTEM and ORGANIZATION scopes; those identities are not tenants.
     */
    private String tenantId;

    private boolean enabled;

    private Date created;

    private Date updated;

}
