package org.kinotic.domain.api.model.security;

/**
 * The outcome of provisioning a {@link MachineParticipantIdentity}: the saved identity and
 * the one and only disclosure of its client secret. The machine connects with
 * {@code (machine.id, clientSecret)} as its credentials; only a hash is stored, so the
 * plaintext here is unrecoverable once discarded.
 *
 * @param machine      the provisioned machine identity
 * @param clientSecret the plaintext client secret, shown once
 */
public record MachineProvisionResult(MachineParticipantIdentity machine, String clientSecret) {
}
