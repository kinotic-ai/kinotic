package org.kinotic.domain.internal.api.rest.support;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.ClusterSerializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.rest.support.OidcFlowPurpose;

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

    /** What the flow is for, which the callback the configuration is registered with dispatches on. */
    private OidcFlowPurpose purpose;

    /**
     * The sign-in scope the flow is for — the organization, the application when the flow signs a user in to
     * one, and the tenant when it signs a user in to one tenant of the application — or {@code null} for a flow
     * that scopes its configuration lookup otherwise.
     */
    private String orgId;
    private String applicationId;
    private String tenantId;

    /**
     * Accept token of the invitation an {@link OidcFlowPurpose#INVITATION} flow is accepting. Carried through
     * the session because IdP redirect URIs are registered exactly, so the token cannot ride in the callback URL.
     */
    private String inviteToken;

    /** The account an {@link OidcFlowPurpose#CONNECT} flow binds the identity to. */
    private String accountId;

    /**
     * Origin of the page that started the flow, which the completed login is bound to, or {@code null}
     * when the start request named no page.
     */
    private String origin;

    @Override
    public void writeToBuffer(Buffer buffer) {
        new JsonObject()
                .put("state", state)
                .put("nonce", nonce)
                .put("pkceVerifier", pkceVerifier)
                .put("configId", configId)
                .put("purpose", purpose == null ? null : purpose.name())
                .put("orgId", orgId)
                .put("applicationId", applicationId)
                .put("tenantId", tenantId)
                .put("inviteToken", inviteToken)
                .put("accountId", accountId)
                .put("origin", origin)
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
        this.purpose = json.getString("purpose") == null ? null : OidcFlowPurpose.valueOf(json.getString("purpose"));
        this.orgId = json.getString("orgId");
        this.applicationId = json.getString("applicationId");
        this.tenantId = json.getString("tenantId");
        this.inviteToken = json.getString("inviteToken");
        this.accountId = json.getString("accountId");
        this.origin = json.getString("origin");
        return read;
    }
}
