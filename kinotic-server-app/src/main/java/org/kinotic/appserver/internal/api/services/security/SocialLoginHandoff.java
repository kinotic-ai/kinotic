package org.kinotic.appserver.internal.api.services.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * A social sign-in the app server's shared callback host verified, awaiting completion at the application's own
 * API host: the application's user the identity the provider returned signs in as, found in the scope the sign-in was
 * to, the application it signs in to, and the page the sign-in belongs to. Staged in {@link SocialLoginHandoffService} under a single-use code the browser carries from the one host
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

    /** The application's user the verified identity signs in as. */
    private String identityId;

    /** Origin of the page that started the sign-in, which the completed login is bound to. */
    private String origin;

    /**
     * The value the application's API host put in the starting browser's session; the completion is answered only
     * to a browser whose session holds it, so the code signs no other browser in.
     */
    private String binding;
}
