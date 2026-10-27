package org.kinotic.domain.api.model.security.identity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.security.participant.ParticipantScope;

/**
 * A non-human principal that authenticates with its own credential rather than on a person's
 * behalf — a system daemon such as the vm-manager (SYSTEM scope), an API client of one
 * application (APPLICATION scope, acting exactly as an application end-user does), or a
 * process acting for a whole organization such as a project deployment's workloads
 * (ORGANIZATION scope). A machine connects through the Kinotic client with the identity's
 * {@code id} as {@code clientId} and the secret issued at provisioning as
 * {@code clientSecret}; the secret is verified against the credential store on every
 * handshake, so disabling a machine cuts it off on its next connection.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public final class MachineParticipantIdentity extends ParticipantIdentity implements ScopedParticipantIdentity {

    /** The organization of the machine's scope, or {@code null} for SYSTEM. */
    private String organizationId;

    /** The application of the machine's scope, or {@code null} unless APPLICATION. */
    private String applicationId;

    /**
     * What the machine is for; set when it is provisioned.
     */
    private MachineType machineType;

    @Override
    public ParticipantScope scope() {
        return new ParticipantScope(organizationId, applicationId, null);
    }

    @Override
    public ParticipantIdentityType getType() {
        return ParticipantIdentityType.MACHINE;
    }
}
