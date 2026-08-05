package org.kinotic.os.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.iam.DelegateSession;
import org.kinotic.domain.api.model.iam.DelegatingParticipantIdentity;
import org.kinotic.domain.api.services.iam.ParticipantIdentityService;
import org.kinotic.domain.api.services.iam.RefreshTokenService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.os.api.services.iam.DelegateService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DefaultDelegateService implements DelegateService {

    private final ParticipantIdentityService identityService;
    private final RefreshTokenService refreshTokenService;

    @Override
    public CompletableFuture<Page<DelegatingParticipantIdentity>> findMyDelegates(Pageable pageable, Participant participant) {
        DomainUtil.requireUserParticipant(participant);
        return identityService.findDelegatesByOwner(participant.getId(), pageable);
    }

    @Override
    public CompletableFuture<List<DelegateSession>> findSessions(String delegateId, Participant participant) {
        return loadOwnedDelegate(delegateId, participant)
                .thenCompose(delegate -> refreshTokenService.findActiveSessions(delegate.getId()));
    }

    @Override
    public CompletableFuture<Void> revokeSession(String delegateId, String familyId, Participant participant) {
        Validate.notBlank(familyId, "familyId is required");
        return loadOwnedDelegate(delegateId, participant)
                .thenCompose(delegate -> refreshTokenService.revokeFamily(delegate.getId(), familyId));
    }

    @Override
    public CompletableFuture<Void> revokeDelegate(String delegateId, Participant participant) {
        return loadOwnedDelegate(delegateId, participant)
                .thenCompose(delegate -> identityService.saveSync(delegate.setEnabled(false)))
                // authentication already rejects a disabled delegate; ending its sessions too
                // keeps the session list truthful and stops rotations at the token endpoint
                .thenCompose(saved -> refreshTokenService.revokeAllFor(delegateId));
    }

    /**
     * Loads a delegate of the calling user for inspection or mutation. Fails for unknown ids
     * and for delegates of other users with the same message — no existence oracle.
     */
    private CompletableFuture<DelegatingParticipantIdentity> loadOwnedDelegate(String delegateId, Participant participant) {
        DomainUtil.requireUserParticipant(participant);
        Validate.notBlank(delegateId, "delegateId is required");
        return identityService.findById(delegateId)
                .thenApply(identity -> {
                    if (!(identity instanceof DelegatingParticipantIdentity delegate)
                            || !participant.getId().equals(delegate.getOwnerId())) {
                        throw new IllegalArgumentException("Delegate not found.");
                    }
                    return delegate;
                });
    }
}
