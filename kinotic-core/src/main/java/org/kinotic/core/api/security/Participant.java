package org.kinotic.core.api.security;

import java.util.List;
import java.util.Map;

/**
 * Stores identifying information about a logged-in participant.
 * <p>
 * The shape of any given participant is determined by which subtype it implements —
 * {@code SystemParticipant}, {@code OrganizationParticipant}, or
 * {@code ApplicationParticipant} (all in {@code kinotic-domain}). Code that needs scope
 * context should match on the subtype rather than reading scope-typed fields off the base
 * interface.
 * <p>
 * WARNING: do not store sensitive information in {@link Participant} as it will be sent
 * to receivers of requests sent by the {@link Participant}.
 * Created by Navíd Mitchell 🤪on 6/16/23.
 */
public interface Participant {
    /**
     * The identity of the participant
     *
     * @return the identity of the participant
     */
    String getId();

    /**
     * Metadata is a map of key value pairs that can be used to store additional information about a participant
     *
     * @return a map of key value pairs
     */
    Map<String, String> getMetadata();

    /**
     * Roles are a list of strings that can be used to authorize a participant to perform certain actions
     *
     * @return a list of roles
     */
    List<String> getRoles();
}
