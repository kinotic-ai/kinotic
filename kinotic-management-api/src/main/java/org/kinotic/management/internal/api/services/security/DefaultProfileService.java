package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.services.security.ProfileService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultProfileService implements ProfileService {

    private final ParticipantIdentityService identityService;

    @Override
    public Future<UserParticipantIdentity> findMyProfile(Participant participant) {
        return loadCallingUser(participant);
    }

    @Override
    public Future<UserParticipantIdentity> updateDisplayName(String displayName, Participant participant) {
        Validate.notBlank(displayName, "displayName is required");
        return loadCallingUser(participant)
                .compose(user -> identityService.save(user.setDisplayName(displayName)))
                .map(UserParticipantIdentity.class::cast);
    }

    /** Loads the identity behind the calling participant. */
    private Future<UserParticipantIdentity> loadCallingUser(Participant participant) {
        DomainUtil.requireUserParticipant(participant);
        return identityService.findById(participant.getId())
                              .map(identity -> {
                                  // a session outlives its row when an admin removes the member mid-session
                                  if (!(identity instanceof UserParticipantIdentity user)) {
                                      throw new IllegalArgumentException("Profile not found.");
                                  }
                                  return user;
                              });
    }
}
