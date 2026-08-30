package org.kinotic.domain.api.model.security.identity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.domain.api.model.security.AuthType;

import java.util.Date;

/**
 * A persisted identity that can authenticate and become a Participant, at any scope layer of
 * the IAM system. The concrete subtype states what kind of principal it is —
 * {@link UserParticipantIdentity} for a person, {@link DelegatingParticipantIdentity} for a
 * client acting on a person's behalf, {@link MachineParticipantIdentity} for a non-human
 * caller with its own credential — and carries the fields only that kind has. The scope
 * is encoded structurally by which of {@link #organizationId} / {@link #applicationId} is set:
 * <ul>
 *   <li>both null → SYSTEM (platform operators)</li>
 *   <li>{@code organizationId} set, {@code applicationId} null → ORGANIZATION</li>
 *   <li>both set → APPLICATION (a user of an Application owned by an Organization)</li>
 * </ul>
 * All subtypes share one id space and one index; {@link #getType()} is the persisted
 * discriminator the document is deserialized by.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = UserParticipantIdentity.class, name = "USER"),
        @JsonSubTypes.Type(value = DelegatingParticipantIdentity.class, name = "DELEGATE"),
        @JsonSubTypes.Type(value = MachineParticipantIdentity.class, name = "MACHINE")
})
public abstract sealed class ParticipantIdentity implements Identifiable<String>
        permits UserParticipantIdentity, DelegatingParticipantIdentity, MachineParticipantIdentity {

    private String id;

    private String displayName;

    /**
     * Authentication method: {@link AuthType#LOCAL} or {@link AuthType#OIDC} for
     * {@link UserParticipantIdentity}, always {@link AuthType#DELEGATED} for
     * {@link DelegatingParticipantIdentity}, always {@link AuthType#CLIENT_CREDENTIALS} for
     * {@link MachineParticipantIdentity}.
     */
    private AuthType authType;

    /**
     * The Organization this identity belongs to. {@code null} for SYSTEM identities; set for
     * both ORGANIZATION and APPLICATION identities (an Application is owned by exactly one
     * Org, so APP-scope identities intrinsically carry the owning org's id).
     */
    private String organizationId;

    /**
     * The Application this identity authenticates against. {@code null} for SYSTEM and
     * ORGANIZATION identities; set for APPLICATION identities. When set,
     * {@link #organizationId} must also be set (the org that owns the application).
     */
    private String applicationId;

    /**
     * The client tenant this identity belongs to within their application's data model.
     * Only meaningful when {@link #applicationId} is set — application tenants partition
     * the data of {@code MultiTenancyType.SHARED} entities owned by the app. Must be null
     * for SYSTEM and ORGANIZATION scopes; those identities are not tenants.
     */
    private String tenantId;

    private boolean enabled;

    private Date created;

    private Date updated;

    /**
     * What kind of principal this identity is; fixed by the concrete subtype and persisted
     * as the polymorphic discriminator of the document.
     */
    public abstract ParticipantIdentityType getType();

}
