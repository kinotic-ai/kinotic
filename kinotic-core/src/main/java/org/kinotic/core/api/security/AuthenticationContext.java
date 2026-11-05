package org.kinotic.core.api.security;

import lombok.Getter;
import org.apache.commons.lang3.Validate;

/**
 * What an entry point tells the {@link SecurityService} about the authentication it asks for, beside the
 * credentials themselves: decided by the entry point, never read from what the caller sent.
 */
@Getter
public class AuthenticationContext {

    /**
     * The label of the surface Kinotic RPC itself serves: the published services reached over the STOMP gateway.
     */
    public static final String PUBLISHED_SERVICES = "kinotic";

    /**
     * The surface the entry point serves, which a token presented to it must have been minted for: the label is
     * the token's {@code aud} value, an open set, since every entry point names its own and an unknown label
     * matches no token. Credentials that are not minted for a surface, a password or a machine secret, ignore it.
     */
    private final String authenticationFor;

    public AuthenticationContext(String authenticationFor) {
        Validate.notBlank(authenticationFor, "authenticationFor must not be blank");
        this.authenticationFor = authenticationFor;
    }

}
