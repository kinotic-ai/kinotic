package org.kinotic.domain.api.model.security;

/**
 * Who owns an {@link OidcConfiguration} and where it is offered. Mirrors the concrete subtype one-to-one —
 * {@link #ORG_SIGNUP} for {@link OrgSignupOidcConfiguration}, {@link #APP_SOCIAL} for
 * {@link ApplicationSocialOidcConfiguration}, {@link #ORGANIZATION} for
 * {@link OrganizationOidcConfiguration}, {@link #APPLICATION} for {@link ApplicationOidcConfiguration},
 * {@link #TENANT} for {@link TenantOidcConfiguration} — and is persisted as the document's polymorphic discriminator, so it is also the value queries
 * filter configurations by kind with.
 */
public enum OidcConfigurationType {
    /**
     * A registration the system holds with a provider, through which organization users sign up and in.
     */
    ORG_SIGNUP,
    /**
     * A registration the system holds with a social provider, which an application's configuration of the
     * same provider kind signs in through when it uses {@link ClientRegistrationType#MANAGED}.
     */
    APP_SOCIAL,
    /**
     * A provider an organization configured for its own login.
     */
    ORGANIZATION,
    /**
     * A provider an organization configured for one of its applications' logins.
     */
    APPLICATION,
    /**
     * A provider an organization configured for the login of one tenant of one of its applications.
     */
    TENANT
}
