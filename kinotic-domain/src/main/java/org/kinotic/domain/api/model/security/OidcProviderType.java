package org.kinotic.domain.api.model.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.lang3.Validate;

import java.util.Set;
import java.util.regex.Pattern;

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
 * A type an organization may configure for itself carries the template of its issuer URL, which
 * {@link #authority(String)} fills from the organization's account at the provider, so an
 * organization never names a URL and the server only ever connects to the provider's own hosts.
 * A type without a template is offered to organizations' sign-up and sign-in through the
 * system's curated registrations only.
 */
public enum OidcProviderType {

    /** Google Workspace and consumer accounts share one issuer, so there is no account to name. */
    GOOGLE("google", "https://accounts.google.com", null, null),
    /**
     * The account is the Entra directory, named by its tenant id, or, for an application's or a tenant's login,
     * {@code common} for every Microsoft account and {@code organizations} for every work or school account.
     */
    AZURE_AD("azure-ad", "https://login.microsoftonline.com/{providerAccount}/v2.0",
             "common|organizations|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
             "Directory (tenant) ID, or common or organizations"),
    /** The account is the Okta org, named by its Okta domain, on any of Okta's own domains. */
    OKTA("okta", "https://{providerAccount}", "[a-z0-9-]{1,63}\\.(okta\\.com|oktapreview\\.com|okta-emea\\.com)", "Okta domain"),
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

    private static final String PROVIDER_ACCOUNT_PLACEHOLDER = "{providerAccount}";
    private static final Set<String> MULTI_DIRECTORY_ACCOUNTS = Set.of("common", "organizations");

    private final String key;
    private final String authorityTemplate;
    private final Pattern providerAccountPattern;
    private final String providerAccountLabel;

    OidcProviderType(String key) {
        this(key, null, null, null);
    }

    OidcProviderType(String key, String authorityTemplate, String providerAccountPattern, String providerAccountLabel) {
        this.key = key;
        this.authorityTemplate = authorityTemplate;
        this.providerAccountPattern = providerAccountPattern != null ? Pattern.compile(providerAccountPattern) : null;
        this.providerAccountLabel = providerAccountLabel;
    }

    /** Canonical key used on the wire, in config storage, and for branding lookup. */
    @JsonValue
    public String key() {
        return key;
    }

    /**
     * Whether an organization may configure a provider of this type for itself: the type has an issuer
     * template the organization's account fills.
     */
    public boolean offeredToOrganizations() {
        return authorityTemplate != null;
    }

    /**
     * What the organization is asked for as its account at this provider, such as {@code "Okta domain"},
     * or {@code null} when the type has one issuer for everyone or is not offered to organizations.
     */
    public String providerAccountLabel() {
        return providerAccountLabel;
    }

    /**
     * Whether {@code providerAccount} admits the users of every directory at this provider rather than one, as
     * Microsoft Entra's {@code common} and {@code organizations} do; the administrator of any of those directories
     * sets its users' emails.
     */
    public boolean admitsEveryDirectory(String providerAccount) {
        return this == AZURE_AD && MULTI_DIRECTORY_ACCOUNTS.contains(providerAccount);
    }

    /**
     * The issuer URL of an organization's account at this provider, from the type's template.
     *
     * @param providerAccount the organization's account at the provider in the form
     *                        {@link #providerAccountLabel()} asks for, or {@code null} for a type with one
     *                        issuer for everyone
     * @return the issuer URL
     * @throws IllegalArgumentException when the type is not offered to organizations, or the account is
     *                                  missing, given to a type that takes none, or not in the form the
     *                                  type expects
     */
    public String authority(String providerAccount) {
        Validate.isTrue(offeredToOrganizations(), "An organization cannot configure a %s provider", key);
        String ret;
        if (providerAccountPattern == null) {
            Validate.isTrue(providerAccount == null || providerAccount.isBlank(), "A %s provider takes no account", key);
            ret = authorityTemplate;
        } else {
            Validate.isTrue(providerAccount != null && providerAccountPattern.matcher(providerAccount).matches(),
                            "The %s is not in the form a %s provider expects", providerAccountLabel, key);
            ret = authorityTemplate.replace(PROVIDER_ACCOUNT_PLACEHOLDER, providerAccount);
        }
        return ret;
    }

    @JsonCreator
    public static OidcProviderType fromKey(String key) {
        for (OidcProviderType k : values()) {
            if (k.key.equals(key)) return k;
        }
        throw new IllegalArgumentException("Unknown OIDC provider type: " + key);
    }
}
