package org.kinotic.gateway.internal.endpoints.rest.support;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.ClusterSerializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Session state for one in-flight OIDC redirect flow.
 *
 * <p>Lives in the Vert.x web session, so it implements {@link ClusterSerializable} to survive a
 * clustered session store. The two legs of an OIDC flow (authorize and callback) can land on
 * different cluster nodes, so this state round-trips between them. Vert.x reconstructs it through
 * the no-arg constructor on read.
 */
@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
public class OidcFlowSession implements ClusterSerializable {

    private String state;
    private String nonce;
    private String pkceVerifier;
    private String configId;
    private String orgId;

    /** Token of the member invitation this flow accepts, or {@code null} for sign-up/login flows. */
    private String inviteToken;

    @Override
    public void writeToBuffer(Buffer buffer) {
        new JsonObject()
                .put("state", state)
                .put("nonce", nonce)
                .put("pkceVerifier", pkceVerifier)
                .put("configId", configId)
                .put("orgId", orgId)
                .put("inviteToken", inviteToken)
                .writeToBuffer(buffer);
    }

    @Override
    public int readFromBuffer(int pos, Buffer buffer) {
        JsonObject json = new JsonObject();
        int read = json.readFromBuffer(pos, buffer);
        this.state = json.getString("state");
        this.nonce = json.getString("nonce");
        this.pkceVerifier = json.getString("pkceVerifier");
        this.configId = json.getString("configId");
        this.orgId = json.getString("orgId");
        this.inviteToken = json.getString("inviteToken");
        return read;
    }
}
