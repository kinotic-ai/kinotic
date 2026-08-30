package org.kinotic.domain.api.model.security.identity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.security.AuthType;

/**
 * A person. Unique by email within their scope, pre-created by an administrator before they
 * can authenticate, using {@link AuthType#LOCAL} credentials or a federated
 * {@link AuthType#OIDC} identity.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public final class UserParticipantIdentity extends ParticipantIdentity {

    /**
     * Email address, unique within the user's scope (organizationId + applicationId).
     */
    private String email;

    /**
     * The {@code sub} claim from the OIDC token. Populated when an OIDC user is provisioned
     * (auto on first login, or via pending sign-up completion); used together with
     * {@link #oidcConfigId} to resolve the user back from the IdP callback.
     */
    private String oidcSubject;

    /**
     * Reference to the OIDC configuration used to provision this user. Distinguishes
     * {@code sub}s issued by different IdPs that might collide.
     */
    private String oidcConfigId;

    @Override
    public ParticipantIdentityType getType() {
        return ParticipantIdentityType.USER;
    }
}
