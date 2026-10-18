package org.kinotic.management.api.model;

/** How a {@link SignInMethod} authenticates its account. */
public enum SignInMethodKind {
    /** The account's password. */
    PASSWORD,
    /** An identity a provider asserts, bound to the account. */
    OIDC
}
