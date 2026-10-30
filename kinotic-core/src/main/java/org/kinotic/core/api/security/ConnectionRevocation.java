package org.kinotic.core.api.security;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A revocation published to every node: the connections of the named identity or login end.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionRevocation {

    /** The Vert.x event bus address revocations are published on. */
    public static final String ADDRESS = "kinotic.security.connection-revocation";

    private ConnectionRevocationType type;
    private String id;

    public JsonObject toJson() {
        return new JsonObject().put("type", type.name()).put("id", id);
    }

    public static ConnectionRevocation fromJson(JsonObject json) {
        return new ConnectionRevocation(ConnectionRevocationType.valueOf(json.getString("type")), json.getString("id"));
    }

}
