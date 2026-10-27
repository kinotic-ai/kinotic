package org.kinotic.domain.api.model.security.identity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.security.participant.ParticipantScope;
import org.kinotic.domain.api.model.security.DelegateType;

/**
 * A client (a CLI install, an MCP host such as an LLM) a user has authorized to act on
 * their behalf. Carries the scope the user was signed in to when authorizing it, is unique by {@code (ownerId, clientKey)} within that scope, and authenticates
 * with the tokens minted when its owner authorized it, holding no credential of its own. Disabling
 * a delegate revokes that client's access without touching the owner.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public final class DelegatingParticipantIdentity extends ParticipantIdentity implements ScopedParticipantIdentity {

    /** The organization of the scope the owner authorized the client in, or {@code null} for SYSTEM. */
    private String organizationId;

    /** The application of the scope the owner authorized the client in, or {@code null} unless APPLICATION. */
    private String applicationId;

    /** The owner's tenant when the owner is a user of an application with tenants, else {@code null}. */
    private String tenantId;

    /**
     * Id of the {@link UserParticipantIdentity} this delegate acts on behalf of; for an organization user, the
     * delegate acts in the one organization its own {@link #organizationId} names.
     */
    private String ownerId;

    /**
     * Stable identity of the authorized client, unique per owner: the CIMD {@code client_id}
     * URL for {@link DelegateType#MCP_CLIENT}, a fixed well-known key for
     * {@link DelegateType#CLI}.
     */
    private String clientKey;

    /**
     * The type of client this delegate represents.
     */
    private DelegateType delegateType;

    @Override
    public ParticipantScope scope() {
        return new ParticipantScope(organizationId, applicationId, tenantId);
    }

    @Override
    public ParticipantIdentityType getType() {
        return ParticipantIdentityType.DELEGATE;
    }
}
