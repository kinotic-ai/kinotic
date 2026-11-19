package org.kinotic.common.api.oidc;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.ClusterSerializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * One in-flight sign-in at a provider, kept in the browser's session between the redirect to the provider and the
 * callback. The two can reach different nodes of a cluster, so it travels with a clustered session store.
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public class OidcFlowState implements ClusterSerializable {

    /** The state, nonce and PKCE verifier the callback is held to. */
    private OidcAuthorization authorization;

    /** The id of the registration the sign-in goes through, by which the caller finds its client again. */
    private String registrationId;

    /** The origin of the page that started the sign-in, or {@code null} when it named none. */
    private String origin;

    /** What else the caller keeps for the callback; never {@code null}. */
    private JsonObject context;

    /**
     * A new sign-in through the registration {@code registrationId}, with a fresh authorization.
     *
     * @param origin  the origin of the page that starts the sign-in, or {@code null} when it names none
     * @param context what else the caller keeps for the callback, or {@code null} for nothing
     */
    public static OidcFlowState create(String registrationId, String origin, JsonObject context) {
        return new OidcFlowState(OidcAuthorization.create(), registrationId, origin,
                                 context != null ? context : new JsonObject());
    }

    @Override
    public void writeToBuffer(Buffer buffer) {
        new JsonObject()
                .put("state", authorization.state())
                .put("nonce", authorization.nonce())
                .put("pkceVerifier", authorization.pkceVerifier())
                .put("registrationId", registrationId)
                .put("origin", origin)
                .put("context", context)
                .writeToBuffer(buffer);
    }

    @Override
    public int readFromBuffer(int pos, Buffer buffer) {
        JsonObject json = new JsonObject();
        int ret = json.readFromBuffer(pos, buffer);
        this.authorization = new OidcAuthorization(json.getString("state"), json.getString("nonce"),
                                                   json.getString("pkceVerifier"));
        this.registrationId = json.getString("registrationId");
        this.origin = json.getString("origin");
        this.context = json.getJsonObject("context");
        return ret;
    }
}
