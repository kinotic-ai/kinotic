package org.kinotic.domain.api.model;

/**
 * A way a user comes to belong to a tenant of an application. An application enables the mechanisms it
 * offers; a user created with none enabled shares one tenant-less set of data with every other user.
 */
public enum OnboardingMechanism {
    /**
     * Every user created in the application receives a tenant of its own, so each user's
     * {@code MultiTenancyType.SHARED} rows are isolated from every other user's. Exclusive with the other
     * mechanisms, which put several users in one tenant.
     */
    TENANT_PER_USER,
    /**
     * A new customer signs up at the application's own sign-up page, which creates the tenant, the customer
     * as its first user, and makes the customer the tenant's administrator.
     */
    TENANT_SIGN_UP,
    /**
     * A tenant's administrator invites colleagues into the tenant from the application's own pages.
     */
    TENANT_INVITE
}
