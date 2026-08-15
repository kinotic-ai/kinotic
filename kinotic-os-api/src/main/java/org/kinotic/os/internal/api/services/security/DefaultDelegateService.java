package org.kinotic.os.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.DelegateSession;
import org.kinotic.domain.api.model.security.DelegatingParticipantIdentity;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.os.api.services.security.DelegateService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultDelegateService implements DelegateService {

    private final ParticipantIdentityService identityService;
    private final RefreshTokenService refreshTokenService;

    @Override
    public Future<Page<DelegatingParticipantIdentity>> findMyDelegates(Pageable pageable, Participant participant) {
        DomainUtil.requireUserParticipant(participant);
        return identityService.findDelegatesByOwner(participant.getId(), pageable);
    }

    @Override
    public Future<List<DelegateSession>> findSessions(String delegateId, Participant participant) {
        return loadOwnedDelegate(delegateId, participant)
                .compose(delegate -> refreshTokenService.findActiveSessions(delegate.getId()));
    }

    @Override
    public Future<Void> revokeSession(String delegateId, String familyId, Participant participant) {
        Validate.notBlank(familyId, "familyId is required");
        return loadOwnedDelegate(delegateId, participant)
                .compose(delegate -> refreshTokenService.revokeFamily(delegate.getId(), familyId));
    }

    @Override
    public Future<Void> revokeDelegate(String delegateId, Participant participant) {
        return loadOwnedDelegate(delegateId, participant)
                .compose(delegate -> identityService.saveSync(delegate.setEnabled(false)))
                // authentication already rejects a disabled delegate; ending its sessions too
                // keeps the session list truthful and stops rotations at the token endpoint
                .compose(saved -> refreshTokenService.revokeAllFor(delegateId));
    }

    /** Loads a delegate of the calling user for inspection or mutation. */
    private Future<DelegatingParticipantIdentity> loadOwnedDelegate(String delegateId, Participant participant) {
        DomainUtil.requireUserParticipant(participant);
        Validate.notBlank(delegateId, "delegateId is required");
        return identityService.findById(delegateId)
                .map(identity -> DomainUtil.requireOwned(identity, DelegatingParticipantIdentity.class,
                        delegate -> participant.getId().equals(delegate.getOwnerId()),
                        "Delegate not found."));
    }
}
