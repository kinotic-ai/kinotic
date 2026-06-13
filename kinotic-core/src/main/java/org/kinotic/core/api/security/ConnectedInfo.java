package org.kinotic.core.api.security;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.ClusterSerializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * Contains information about a connected client.
 *
 * <p>Implements {@link ClusterSerializable} so it can live in a Vert.x web session backed by a
 * clustered session store, which marshals session values between nodes.
 *
 * Created by Navíd Mitchell 🤪on 7/11/23.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ConnectedInfo implements ClusterSerializable {

    /**
     * Vert.x web-session attribute key under which an authenticated {@code ConnectedInfo} is
     * stored. The browser session-login flow writes it at login time and the STOMP handshake
     * reads it back, so the browser authenticates by its session cookie without a token.
     */
    public static final String SESSION_KEY = ConnectedInfo.class.getName();

    /**
     * -- SETTER --
     *  Provides the Jackson mapper used to marshal into and out of a clustered session.
     *  Must be supplied once during startup with a mapper that understands the polymorphic
     *  subtypes (kinotic-domain configures one). A clustered store
     *  reconstructs this type through its no-arg constructor, so the mapper cannot be injected.
     */
    @Setter
    private static volatile JsonMapper serializationMapper;

    /**
     * The connected clients {@link Participant}.
     */
    private Participant participant;
    /**
     * The connected clients reply to id.
     * This id is the only valid "reply-to" scope that can be used by the client.
     */
    private String replyToId;

    @Override
    public void writeToBuffer(Buffer buffer) {
        byte[] json = mapper().writeValueAsString(this).getBytes(StandardCharsets.UTF_8);
        buffer.appendInt(json.length).appendBytes(json);
    }

    @Override
    public int readFromBuffer(int pos, Buffer buffer) {
        int length = buffer.getInt(pos);
        pos += Integer.BYTES;
        byte[] json = buffer.getBytes(pos, pos + length);
        pos += length;
        ConnectedInfo decoded = mapper().readValue(new String(json, StandardCharsets.UTF_8), ConnectedInfo.class);
        this.participant = decoded.participant;
        this.replyToId = decoded.replyToId;
        return pos;
    }

    private static JsonMapper mapper() {
        JsonMapper mapper = serializationMapper;
        if (mapper == null) {
            throw new IllegalStateException("ConnectedInfo serialization mapper has not been configured; "
                                                    + "call setSerializationMapper during startup");
        }
        return mapper;
    }
}
