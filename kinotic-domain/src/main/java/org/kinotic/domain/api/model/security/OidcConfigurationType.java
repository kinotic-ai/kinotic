package org.kinotic.domain.api.model.security;

/**
 * Who owns an {@link OidcConfiguration}. Mirrors the concrete subtype one-to-one — {@link #PLATFORM}
 * for {@link PlatformOidcConfiguration}, {@link #ORGANIZATION} for {@link OrganizationOidcConfiguration}
 * — and is persisted as the document's polymorphic discriminator, so it is also the value queries
 * filter configurations by kind with.
 */
public enum OidcConfigurationType {
    PLATFORM,
    ORGANIZATION
}
