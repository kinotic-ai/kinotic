package org.kinotic.os.internal.api.services.security;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.UserParticipantIdentity;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.os.api.services.security.ProfileService;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DefaultProfileService implements ProfileService {

    private final ParticipantIdentityService identityService;

    @Override
    public CompletableFuture<UserParticipantIdentity> findMyProfile(Participant participant) {
        return loadCallingUser(participant);
    }

    @Override
    public CompletableFuture<UserParticipantIdentity> updateDisplayName(String displayName, Participant participant) {
        Validate.notBlank(displayName, "displayName is required");
        return loadCallingUser(participant)
                .thenCompose(user -> identityService.save(user.setDisplayName(displayName)))
                .thenApply(UserParticipantIdentity.class::cast);
    }

    /** Loads the identity behind the calling participant. */
    private CompletableFuture<UserParticipantIdentity> loadCallingUser(Participant participant) {
        DomainUtil.requireUserParticipant(participant);
        return identityService.findById(participant.getId())
                              .thenApply(identity -> {
                                  // a session outlives its row when an admin removes the member mid-session
                                  if (!(identity instanceof UserParticipantIdentity user)) {
                                      throw new IllegalArgumentException("Profile not found.");
                                  }
                                  return user;
                              });
    }
}
