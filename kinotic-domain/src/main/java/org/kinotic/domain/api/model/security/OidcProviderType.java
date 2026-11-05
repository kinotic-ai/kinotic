package org.kinotic.domain.api.model.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Set;

/**
 * Canonical identifiers for OIDC provider types. Each value maps 1:1 to a Vert.x
 * provider factory class (e.g. {@code GoogleAuth.discover}, {@code AzureADAuth.discover}),
 * and flows unchanged from the frontend branding table through the login-start request,
 * the persisted {@link OidcConfiguration#getProvider()} field, and the backend factory
 * switch.
 * <p>
 * The wire format is the {@code key()} string (e.g. {@code "azure-ad"}), not the Java
 * constant name ({@code AZURE_AD}). {@link JsonValue}/{@link JsonCreator} make Jackson
 * round-trip through the key in both directions, and Spring's relaxed binder uses the
 * same path when binding from helm values or env vars.
 * <p>
 * Every organization may configure {@link #GOOGLE} for itself with a client of its own registration;
 * {@link #OKTA} and {@link #AZURE_AD} are Kinotic Enterprise Features. The server turns the organization's account
 * at the provider into the issuer URL.
 */
public enum OidcProviderType {

    GOOGLE("google"),
    /**
     * Microsoft Entra. The account is the Entra directory, named by its tenant id, or, for an application's or a
     * tenant's login, {@code common} for every Microsoft account and {@code organizations} for every work or school
     * account.
     */
    AZURE_AD("azure-ad"),
    OKTA("okta"),
    MICROSOFT_LIVE("microsoft-live"),
    GITHUB("github"),
    APPLE("apple"),
    FACEBOOK("facebook"),
    LINKEDIN("linkedin"),
    SALESFORCE("salesforce"),
    AMAZON_COGNITO("amazon-cognito"),
    KEYCLOAK("keycloak"),
    AUTH0("auth0"),
    /** Generic OIDC-compliant provider — used with {@code OpenIDConnectAuth.discover}. */
    OIDC("oidc");

    private static final Set<String> MULTI_DIRECTORY_ACCOUNTS = Set.of("common", "organizations");

    private final String key;

    OidcProviderType(String key) {
        this.key = key;
    }

    /** Canonical key used on the wire, in config storage, and for branding lookup. */
    @JsonValue
    public String key() {
        return key;
    }

    /**
     * Whether {@code providerAccount} admits the users of every directory at this provider rather than one, as
     * Microsoft Entra's {@code common} and {@code organizations} do; the administrator of any of those directories
     * sets its users' emails.
     */
    public boolean admitsEveryDirectory(String providerAccount) {
        return this == AZURE_AD && MULTI_DIRECTORY_ACCOUNTS.contains(providerAccount);
    }

    @JsonCreator
    public static OidcProviderType fromKey(String key) {
        for (OidcProviderType k : values()) {
            if (k.key.equals(key)) return k;
        }
        throw new IllegalArgumentException("Unknown OIDC provider type: " + key);
    }
}
