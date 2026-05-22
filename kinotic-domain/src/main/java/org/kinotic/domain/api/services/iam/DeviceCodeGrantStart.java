package org.kinotic.domain.api.services.iam;

import java.util.Date;

/**
 * A freshly started device authorization. The plaintext {@code deviceCode} and
 * {@code userCode} are available only here — the server stores only a hash of the
 * device code.
 */
public record DeviceCodeGrantStart(String deviceCode,
                                   String userCode,
                                   Date expiresAt,
                                   int intervalSeconds) {}
