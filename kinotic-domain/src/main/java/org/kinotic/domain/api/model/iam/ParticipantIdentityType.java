package org.kinotic.domain.api.model.iam;

/**
 * What kind of principal a {@link ParticipantIdentity} represents.
 * <ul>
 *   <li>{@link #USER} — a person, unique by email within their scope, authenticating with
 *       {@link AuthType#LOCAL} credentials or a federated {@link AuthType#OIDC} identity.</li>
 *   <li>{@link #DELEGATE} — a client (a CLI install, an MCP host such as an LLM) a USER has
 *       authorized to act on their behalf. Carries the owning user's scope, is unique by
 *       {@code (ownerId, clientKey)}, and authenticates with {@link AuthType#DELEGATED}
 *       tokens only. Disabling a delegate revokes that client's access without touching
 *       the owner.</li>
 * </ul>
 */
public enum ParticipantIdentityType {
    USER,
    DELEGATE
}
