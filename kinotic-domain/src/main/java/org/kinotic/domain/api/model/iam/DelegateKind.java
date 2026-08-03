package org.kinotic.domain.api.model.iam;

/**
 * The kind of client a {@link ParticipantIdentityType#DELEGATE} identity represents,
 * matching the OAuth grant that authorizes it.
 */
public enum DelegateKind {

    /**
     * The Kinotic CLI, authorized through the RFC 8628 device-code grant. One delegate per
     * user covers every machine; each machine's login is a refresh-token family under it.
     */
    CLI,

    /**
     * An MCP host (e.g. an LLM assistant), authorized through the PKCE authorization-code
     * grant. The client's CIMD {@code client_id} URL is the {@code clientKey}, so the same
     * host authorizing from two devices resolves to one delegate.
     */
    MCP_CLIENT
}
