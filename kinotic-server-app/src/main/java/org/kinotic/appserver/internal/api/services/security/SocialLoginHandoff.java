package org.kinotic.appserver.internal.api.services.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * A social sign-in the app server's shared callback host verified, awaiting completion at the application's own
 * API host: the identity the provider returned, the application it signs in to, and the page the sign-in belongs
 * to. Staged in {@link SocialLoginHandoffService} under a single-use code the browser carries from the one host
 * to the other.
 *
 * <p>Implements {@link Serializable} so Ignite can ship the value across cluster nodes.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class SocialLoginHandoff implements Serializable {

    private String organizationId;

    private String applicationId;

    /** The tenant of the application the sign-in is to, or {@code null} for the application itself. */
    private String tenantId;

    /** The configuration the identity was verified through. */
    private String configurationId;

    /** The provider's {@code sub} claim for the identity. */
    private String subject;

    /** Origin of the page that started the sign-in, which the completed login is bound to. */
    private String origin;
}
