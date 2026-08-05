package org.kinotic.domain.api.model.iam;

/**
 * Outcome of a CLI poll. {@link #user()} is non-null only when {@link #status()} is
 * {@link PollStatus#APPROVED}; {@link #deviceName()} is the optional name supplied when the
 * flow started, carried through so redemption can label the issued token family.
 */
public record DeviceCodePollResult(PollStatus status, UserParticipantIdentity user, String deviceName) {}
