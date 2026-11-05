package org.kinotic.domain.internal.api.ee.license;

/**
 * The Enterprise Features a Kinotic Enterprise License key turns on, named in the key's {@code features} claim by
 * their constant names.
 */
public enum EnterpriseFeature {
    /** Azure Key Vault secret storage. */
    AZURE_KEY_VAULT,
    /** Okta and Microsoft Entra sign-in through an organization's own client. */
    ENTERPRISE_IDENTITY_PROVIDERS
}
