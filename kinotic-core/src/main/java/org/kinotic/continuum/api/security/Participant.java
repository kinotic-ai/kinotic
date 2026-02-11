package org.kinotic.continuum.api.security;

import org.kinotic.continuum.api.Identifiable;

import java.util.List;
import java.util.Map;

/**
 * Stores identifying information about a logged in participant
 * WARNING: do not store sensitive information in {@link Participant} as it will be sent to receivers of requests sent by the {@link Participant}
 * Created by Navíd Mitchell 🤪on 6/16/23.
 */
public interface Participant extends Identifiable<String> {
    /**
     * The identity of the participant
     *
     * @return the identity of the participant
     */
    @Override
    String getId();

    /**
     * The tenant that the participant belongs to
     *
     * @return the tenant or null if not using multi-tenancy
     */
    String getTenantId();

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
