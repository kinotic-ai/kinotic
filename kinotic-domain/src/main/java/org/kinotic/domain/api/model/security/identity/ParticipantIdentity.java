package org.kinotic.domain.api.model.security.identity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * A persisted identity that can authenticate and become a Participant, at any scope layer of
 * the IAM system. The concrete subtype states what kind of principal it is —
 * a {@link UserParticipantIdentity} subtype for a person of one scope, {@link DelegatingParticipantIdentity}
 * for a client acting on a person's behalf, {@link MachineParticipantIdentity} for a non-human
 * caller with its own credential — and carries the fields only that kind has. A
 * {@link SystemParticipantIdentity} acts in the SYSTEM scope; the others are
 * {@link ScopedParticipantIdentity}s, which carry the scope they act in.
 * All subtypes share one id space and one index; {@link #getType()} is the persisted
 * discriminator the document is deserialized by.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SystemParticipantIdentity.class, name = "SYSTEM_USER"),
        @JsonSubTypes.Type(value = OrganizationParticipantIdentity.class, name = "ORGANIZATION_USER"),
        @JsonSubTypes.Type(value = ApplicationParticipantIdentity.class, name = "APPLICATION_USER"),
        @JsonSubTypes.Type(value = DelegatingParticipantIdentity.class, name = "DELEGATE"),
        @JsonSubTypes.Type(value = MachineParticipantIdentity.class, name = "MACHINE")
})
public abstract sealed class ParticipantIdentity implements Identifiable<String>
        permits UserParticipantIdentity, DelegatingParticipantIdentity, MachineParticipantIdentity {

    private String id;

    private String displayName;

    private boolean enabled;

    private Date created;

    private Date updated;

    /**
     * What kind of principal this identity is; fixed by the concrete subtype and persisted
     * as the polymorphic discriminator of the document.
     */
    public abstract ParticipantIdentityType getType();

}
