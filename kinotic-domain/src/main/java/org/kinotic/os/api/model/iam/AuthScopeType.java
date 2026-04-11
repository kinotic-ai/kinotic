package org.kinotic.os.api.model.iam;

/**
 * Well-known IAM scope layers. Each layer maintains completely separate user pools.
 * SYSTEM is for platform operators, ORGANIZATION is for development teams,
 * and APPLICATION is for end-users consuming deployed applications.
 * <p>
 * The {@link IamUser#getAuthScopeType()} field is a String for extensibility,
 * but these are the well-known values used internally for routing OIDC config lookups.
 */
public enum AuthScopeType {
    SYSTEM,
    ORGANIZATION,
    APPLICATION
}
