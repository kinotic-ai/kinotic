package org.kinotic.domain.api.model.security.identity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A non-human principal that authenticates with its own credential rather than on a person's
 * behalf — a platform daemon such as the vm-manager (SYSTEM scope), a project's deploy and
 * runtime workloads hosting its services (ORGANIZATION scope), or an API client of one
 * application (APPLICATION scope, acting exactly as an application end-user does). A machine
 * connects through the Kinotic client with the identity's {@code id} as {@code clientId} and
 * the secret issued at provisioning as {@code clientSecret}; the secret is verified against
 * the credential store on every handshake, so disabling a machine cuts it off on its next
 * connection.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public final class MachineParticipantIdentity extends ParticipantIdentity {

    @Override
    public ParticipantIdentityType getType() {
        return ParticipantIdentityType.MACHINE;
    }
}
