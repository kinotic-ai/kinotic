package org.kinotic.management.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;

/**
 * The signed-in user's own account details, read and edited from the web app's account
 * settings. Every operation addresses the calling user — no parameter names whose profile is
 * meant — and only a person (never a delegate) may call these at all.
 */
@Publish
public interface ProfileService {

    /** Returns the calling user's identity. */
    Future<UserParticipantIdentity> findMyProfile(Participant participant);

    /**
     * Sets the calling user's display name, the name shown wherever they appear in the
     * platform, and returns the saved identity.
     *
     * @param displayName the name to show, required
     */
    Future<UserParticipantIdentity> updateDisplayName(String displayName, Participant participant);
}
