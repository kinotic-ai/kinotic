package org.kinotic.continuum.api.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contains information about a connected client.
 * Created by Navíd Mitchell 🤪on 7/11/23.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ConnectedInfo {
    /**
     * The connected clients {@link Participant}.
     */
    private Participant participant;
    /**
     * The connected clients reply to id.
     * This id is the only valid "reply-to" scope that can be used by the client.
     */
    private String replyToId;
    /**
     * The connected clients session id.
     */
    private String sessionId;

}
