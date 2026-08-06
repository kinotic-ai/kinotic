package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A non-human principal that authenticates with its own credential rather than on a person's
 * behalf — a platform daemon such as the vm-manager, or an external caller of an
 * organization's application API. Authenticates through the RFC 6749 client-credentials
 * grant: the identity's {@code id} is the OAuth {@code client_id}, and the secret issued at
 * provisioning is verified against the credential store. Machines hold no refresh tokens —
 * they re-authenticate with their secret — and disabling one cuts it off on its next request.
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
