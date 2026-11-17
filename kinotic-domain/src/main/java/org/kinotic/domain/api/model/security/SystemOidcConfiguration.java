package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A registration with the identity provider of the people who run this Kinotic deployment, which the system
 * console's login offers. It names one directory of that provider, and an account of the directory whose email is
 * on {@link #allowedEmailDomain} signs in as a system user, created on its first sign-in. Saved by a system user
 * only.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public final class SystemOidcConfiguration extends ManagedOidcConfiguration {

    /**
     * The email domain an account must belong to, such as {@code example.com}. Every account of the directory on
     * this domain holds the system's full authority once signed in. Does not check that the directory owns the
     * domain.
     */
    private String allowedEmailDomain;

    @Override
    public OidcConfigurationType getType() {
        return OidcConfigurationType.SYSTEM;
    }
}
