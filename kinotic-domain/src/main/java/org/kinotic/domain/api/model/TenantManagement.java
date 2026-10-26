package org.kinotic.domain.api.model;

/**
 * Who creates and administers an application's tenants.
 */
public enum TenantManagement {

    /**
     * The system: every user gets a tenant of its own at creation, and every row the user writes is
     * isolated in it.
     */
    SYSTEM,

    /**
     * Tenant administrators: a customer signing up, or the organization in the portal, creates a tenant;
     * its administrator decides who joins and what they hold, within what the application allows. Every user
     * belongs to one of the application's tenants, named when the user is added.
     */
    TENANT_ADMINISTRATORS
}
