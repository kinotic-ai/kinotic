package org.kinotic.core.api.security;

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
     * Vert.x web-session attribute key under which an authenticated {@code ConnectedInfo} is
     * stored. The browser session-login flow writes it at login time and the STOMP handshake
     * reads it back, so the browser authenticates by its session cookie without a token.
     */
    public static final String SESSION_KEY = ConnectedInfo.class.getName();

    /**
     * The connected clients {@link Participant}.
     */
    private Participant participant;
    /**
     * The connected clients reply to id.
     * This id is the only valid "reply-to" scope that can be used by the client.
     */
    private String replyToId;

}
