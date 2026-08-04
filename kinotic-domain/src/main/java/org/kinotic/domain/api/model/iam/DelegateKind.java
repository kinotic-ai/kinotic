package org.kinotic.domain.api.model.iam;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The kind of client a {@link DelegatingParticipantIdentity} represents, matching the OAuth
 * grant that authorizes it. Each kind fixes the {@link KinoticAudience} its tokens are minted
 * for, so a delegate's lineage can never carry a surface its grant did not serve.
 */
@Getter
@RequiredArgsConstructor
public enum DelegateKind {

    /**
     * The Kinotic CLI, authorized through the RFC 8628 device-code grant. One delegate per
     * user covers every machine; each machine's login is a refresh-token family under it.
     */
    CLI(KinoticAudience.PUBLISHED_SERVICES),

    /**
     * An MCP host (e.g. an LLM assistant), authorized through the PKCE authorization-code
     * grant. The client's CIMD {@code client_id} URL is the {@code clientKey}, so the same
     * host authorizing from two devices resolves to one delegate.
     */
    MCP_CLIENT(KinoticAudience.MCP_TOOLS);

    /** The surface access tokens minted for this kind of delegate are valid for. */
    private final KinoticAudience audience;
}
