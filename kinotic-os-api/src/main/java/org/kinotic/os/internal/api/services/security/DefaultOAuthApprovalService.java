package org.kinotic.os.internal.api.services.security;

import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.PendingOAuthAuthorization;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.api.services.security.DeviceCodeGrantService;
import org.kinotic.domain.api.services.security.OAuthAuthorizationService;
import org.kinotic.os.api.services.security.OAuthApprovalService;
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
        DomainUtil.requireUserParticipant(participant);
        return oauthAuthorizationService.approve(requestId, participant.getId());
    }

    @Override
    public CompletableFuture<String> deny(String requestId) {
        return oauthAuthorizationService.deny(requestId);
    }

    @Override
    public CompletableFuture<Void> approveDevice(String userCode, Participant participant) {
        // consent must come from a person: a delegate approving grants could mint itself
        // further delegates on the user's behalf without the user ever seeing a consent screen
        DomainUtil.requireUserParticipant(participant);
        return deviceCodeGrantService.approve(userCode, participant.getId());
    }
}
