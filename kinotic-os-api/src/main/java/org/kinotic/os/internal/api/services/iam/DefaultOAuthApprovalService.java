package org.kinotic.os.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.iam.PendingOAuthAuthorization;
import org.kinotic.domain.api.services.iam.DeviceCodeGrantService;
import org.kinotic.domain.api.services.iam.OAuthAuthorizationService;
import org.kinotic.os.api.services.iam.OAuthApprovalService;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DefaultOAuthApprovalService implements OAuthApprovalService {

    private final OAuthAuthorizationService oauthAuthorizationService;
    private final DeviceCodeGrantService deviceCodeGrantService;

    @Override
    public CompletableFuture<PendingOAuthAuthorization> describe(String requestId) {
        return oauthAuthorizationService.findPending(requestId);
    }

    @Override
    public CompletableFuture<String> approve(String requestId, Participant participant) {
        requireUser(participant);
        return oauthAuthorizationService.approve(requestId, participant.getId());
    }

    @Override
    public CompletableFuture<String> deny(String requestId) {
        return oauthAuthorizationService.deny(requestId);
    }

    @Override
    public CompletableFuture<Void> approveDevice(String userCode, Participant participant) {
        requireUser(participant);
        return deviceCodeGrantService.approve(userCode, participant.getId());
    }

    // consent must come from a person: a delegate approving grants could mint itself
    // further delegates on the user's behalf without the user ever seeing a consent screen
    private static void requireUser(Participant participant) {
        String type = participant.getMetadata().get(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY);
        if (!ParticipantConstants.PARTICIPANT_TYPE_USER.equals(type)) {
            throw new IllegalArgumentException("Only a signed-in user may approve an authorization request");
        }
    }
}
