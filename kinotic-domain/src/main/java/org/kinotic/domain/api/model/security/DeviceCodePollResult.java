package org.kinotic.domain.api.model.security;

import org.kinotic.core.api.security.Participant;

/**
 * Outcome of a CLI poll. {@link #approver()} is non-null only when {@link #status()} is
 * {@link PollStatus#APPROVED}: the participant that approved the grant, in the scope it was signed in to,
 * which the issued delegate acts in. {@link #deviceName()} is the optional name supplied when the flow
 * started, carried through so redemption can label the issued token family.
 */
public record DeviceCodePollResult(PollStatus status, Participant approver, String deviceName) {}
