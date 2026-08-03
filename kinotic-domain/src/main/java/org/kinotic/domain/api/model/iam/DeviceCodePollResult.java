package org.kinotic.domain.api.model.iam;

/**
 * Outcome of a CLI poll. {@link #user()} is non-null only when {@link #status()} is
 * {@link PollStatus#APPROVED}.
 */
public record DeviceCodePollResult(PollStatus status, ParticipantIdentity user) {}
