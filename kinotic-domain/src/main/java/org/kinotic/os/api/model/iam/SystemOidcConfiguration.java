package org.kinotic.os.api.model.iam;

import lombok.NoArgsConstructor;

/**
 * OIDC configuration that gates access to Kinotic platform administration. Backed by a
 * dedicated Microsoft Entra application — separate from the social-signup configs so
 * the admin IdP can be rotated independently.
 *
 * <p>Carries no client secret: platform admins are fully managed in Entra (the IdP
 * owns the user lifecycle) and the OIDC flow runs as a public client with PKCE, so
 * Kinotic doesn't need to authenticate to the token endpoint as a confidential client.
 *
 * <p>Singleton-shaped today (one row); the table can hold multiple rows if the
 * platform later wants to allow multiple federated identity providers for admins.
 */
@NoArgsConstructor
public class SystemOidcConfiguration extends BaseOidcConfiguration {
}
