package org.kinotic.domain.internal.utils;

import org.kinotic.core.api.security.DefaultParticipant;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.domain.api.model.iam.IamUser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a persisted {@link IamUser} to the {@link Participant} security identity carried for
 * the life of a connection. This is the single definition of that mapping — the participant
 * metadata keys and the scope-to-tenant rule live here so the authentication paths and the
 * browser session-login flow stay consistent.
 */
public class ParticipantUtil {

    /**
     * Builds the {@link Participant} for an authenticated {@code user}.
     */
    public static Participant fromUser(IamUser user) {
        Map<String, String> metadata = new HashMap<>(Map.of(
                ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER,
                "email", user.getEmail(),
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getEmail(),
                "authType", user.getAuthType().name()
        ));

        // tenantId is the client-tenant the caller is acting within — meaningful only for
        // APPLICATION-scoped users (where it partitions SHARED entity data). SYSTEM and ORGANIZATION
        // identities are not tenants, so user.getTenantId() must be null for them.
        return new DefaultParticipant(user.getTenantId(), user.getId(),
                user.getAuthScopeType(), user.getAuthScopeId(), metadata, List.of());
    }
}
