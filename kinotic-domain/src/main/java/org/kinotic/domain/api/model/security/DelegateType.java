package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kinotic.domain.api.model.security.identity.DelegatingParticipantIdentity;
import org.kinotic.idl.api.utils.AuthzConstants;

import java.util.function.BiPredicate;

/**
 * The type of client a {@link DelegatingParticipantIdentity} represents, matching the OAuth
 * grant that authorizes it. Each type fixes the {@link KinoticAudience} its tokens are minted
 * for, so a delegate's lineage can never carry a surface its grant did not serve, and the
 * permissions a delegate of the type may exercise of its owner's, the type's ceiling.
 */
@RequiredArgsConstructor
public enum DelegateType {

    /**
     * The Kinotic CLI, authorized through the RFC 8628 device-code grant. One delegate per
     * user covers every machine; each machine's login is a refresh-token family under it. It
     * reads and edits projects and entity definitions, which is what synchronizing a project needs.
     */
    CLI(KinoticAudience.PUBLISHED_SERVICES,
        (type, permission) -> (AuthzConstants.PROJECT_TYPE.equals(type) || AuthzConstants.ENTITY_DEFINITION_TYPE.equals(type))
                && (AuthzConstants.CAN_READ.equals(permission) || AuthzConstants.CAN_EDIT.equals(permission))),

    /**
     * An MCP host (e.g. an LLM assistant), authorized through the PKCE authorization-code
     * grant. The client's CIMD {@code client_id} URL is the {@code clientKey}, so the same
     * host authorizing from two devices resolves to one delegate. It exercises every permission
     * of its owner's but those deciding who may act, {@link AuthzConstants#AUTHORITY_PERMISSIONS}.
     */
    MCP_CLIENT(KinoticAudience.MCP_TOOLS, (type, permission) -> !AuthzConstants.AUTHORITY_PERMISSIONS.contains(permission));

    /** The surface access tokens minted for this type of delegate are valid for. */
    @Getter
    private final KinoticAudience audience;

    private final BiPredicate<String, String> ceiling;

    /**
     * Whether a delegate of this type may exercise a permission its owner holds.
     *
     * @param type       the resource type the permission is about, such as {@code project}
     * @param permission the permission's short name, such as {@code can_edit}
     * @return true when the permission is within the type's ceiling
     */
    public boolean permits(String type, String permission) {
        return ceiling.test(type, permission);
    }
}
