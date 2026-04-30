package org.kinotic.os.api.model.iam;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Canonical identifiers for OIDC provider kinds. Each value maps 1:1 to a Vert.x
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
 * Use {@link #OIDC} as the generic escape hatch for any standards-compliant OIDC provider
 * that lacks a dedicated factory (e.g. Okta, Ping, Duende); the backend will invoke
 * {@code OpenIDConnectAuth.discover} with the configured {@code authority}.
 */
public enum OidcProviderKind {

    GOOGLE("google"),
    AZURE_AD("azure-ad"),
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

    private final String key;

    OidcProviderKind(String key) {
        this.key = key;
    }

    /** Canonical key used on the wire, in config storage, and for branding lookup. */
    @JsonValue
    public String key() {
        return key;
    }

    @JsonCreator
    public static OidcProviderKind fromKey(String key) {
        for (OidcProviderKind k : values()) {
            if (k.key.equals(key)) return k;
        }
        throw new IllegalArgumentException("Unknown OIDC provider kind: " + key);
    }
}
