package org.kinotic.os.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.iam.PendingOAuthAuthorization;
import org.kinotic.domain.api.services.iam.OAuthAuthorizationService;
import org.kinotic.os.api.services.iam.OAuthApprovalService;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DefaultOAuthApprovalService implements OAuthApprovalService {

    private final OAuthAuthorizationService oauthAuthorizationService;

    @Override
    public CompletableFuture<PendingOAuthAuthorization> describe(String requestId) {
        return oauthAuthorizationService.findPending(requestId);
    }

    @Override
    public CompletableFuture<String> approve(String requestId, Participant participant) {
        return oauthAuthorizationService.approve(requestId, participant.getId());
    }

    @Override
    public CompletableFuture<String> deny(String requestId) {
        return oauthAuthorizationService.deny(requestId);
    }
}
