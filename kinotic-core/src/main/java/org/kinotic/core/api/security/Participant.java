package org.kinotic.core.api.security;

import java.util.List;
import java.util.Map;

/**
 * Stores identifying information about a logged-in participant
 * WARNING: do not store sensitive information in {@link Participant} as it will be sent to receivers of requests sent by the {@link Participant}
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
     * The tenant that the participant belongs to
     *
     * @return the tenant or null if not using multi-tenancy
     */
    String getTenantId();

    /**
     * The scope layer this participant authenticated against.
     * Well-known values are "SYSTEM", "ORGANIZATION", and "APPLICATION",
     * but custom values are allowed for extensibility.
     *
     * @return the auth scope type, or null if not using scoped authentication
     */
    String getAuthScopeType();

    /**
     * The identifier of the specific scope this participant belongs to.
     * For example, "kinotic" for system scope, an organization ID, or an application ID.
     * Together with {@link #getAuthScopeType()}, uniquely identifies which user pool
     * this participant was authenticated from.
     *
     * @return the auth scope id, or null if not using scoped authentication
     */
    String getAuthScopeId();

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
