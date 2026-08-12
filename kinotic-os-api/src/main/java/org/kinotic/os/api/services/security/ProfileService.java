package org.kinotic.os.api.services.security;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.UserParticipantIdentity;

import java.util.concurrent.CompletableFuture;

/**
 * The signed-in user's own account details, read and edited from the web app's account
 * settings. Every operation addresses the calling user — no parameter names whose profile is
 * meant — and only a person (never a delegate) may call these at all.
 */
@Publish
public interface ProfileService {

    /** Returns the calling user's identity. */
    CompletableFuture<UserParticipantIdentity> findMyProfile(Participant participant);

    /**
     * Sets the calling user's display name, the name shown wherever they appear in the
     * platform, and returns the saved identity.
     *
     * @param displayName the name to show, required
     */
    CompletableFuture<UserParticipantIdentity> updateDisplayName(String displayName, Participant participant);
}
