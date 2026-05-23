package org.kinotic.domain.api.services.iam;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.security.Participant;

import java.util.concurrent.CompletableFuture;

/**
 * Browser-invoked service for the RFC 8628 device-authorization {@code approve} step. The
 * signed-in browser user invokes {@link #approve} with the {@code user_code} shown in their
 * CLI; the framework injects the calling {@link Participant} so the grant is bound to that
 * user.
 */
@Publish
public interface DeviceApprovalService {

    /**
     * Binds the calling participant's user to the pending device-authorization grant
     * identified by {@code userCode}. Fails if the code is unknown, already approved, or
     * expired.
     *
     * @param userCode the code the user entered in the browser
     */
    CompletableFuture<Void> approve(String userCode, Participant participant);
}
