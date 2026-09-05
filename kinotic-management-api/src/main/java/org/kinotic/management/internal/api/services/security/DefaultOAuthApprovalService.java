package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.PendingOAuthAuthorization;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.api.services.security.DeviceCodeGrantService;
import org.kinotic.domain.api.services.security.OAuthAuthorizationService;
import org.kinotic.management.api.services.security.OAuthApprovalService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultOAuthApprovalService implements OAuthApprovalService {

    private final OAuthAuthorizationService oauthAuthorizationService;
    private final DeviceCodeGrantService deviceCodeGrantService;

    @Override
    public Future<PendingOAuthAuthorization> describe(String requestId) {
        return oauthAuthorizationService.findPending(requestId);
    }

    @Override
    public Future<String> approve(String requestId, Participant participant) {
        DomainUtil.requireUserParticipant(participant);
        return oauthAuthorizationService.approve(requestId, participant.getId());
    }

    @Override
    public Future<String> deny(String requestId) {
        return oauthAuthorizationService.deny(requestId);
    }

    @Override
    public Future<Void> approveDevice(String userCode, Participant participant) {
        // consent must come from a person: a delegate approving grants could mint itself
        // further delegates on the user's behalf without the user ever seeing a consent screen
        DomainUtil.requireUserParticipant(participant);
        return deviceCodeGrantService.approve(userCode, participant.getId());
    }
}
