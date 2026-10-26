package org.kinotic.domain.api.model.security;

/** How a sign-in provider a login offers signs a person in. */
public enum LoginProviderType {
    /** With an email and a password, posted to the login's password route. */
    PASSWORD,
    /** Through an OIDC provider, started at the login's start route for the provider's configuration. */
    OIDC
}
