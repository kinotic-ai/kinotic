

package org.kinotic.core.api.security;

/**
 * Some common constants used for the {@link Participant} and {@link Participant#getMetadata()}
 * Created by navid on 2/3/20
 */
public class ParticipantConstants {

    public static final String PARTICIPANT_TYPE_METADATA_KEY = "type";

    public static final String PARTICIPANT_TYPE_USER = "user";

    /** A client (CLI, MCP host) acting on behalf of a user that authorized it. */
    public static final String PARTICIPANT_TYPE_DELEGATE = "delegate";

    /** A non-human caller with its own credential — a platform daemon or an external API client. */
    public static final String PARTICIPANT_TYPE_MACHINE = "machine";

}

