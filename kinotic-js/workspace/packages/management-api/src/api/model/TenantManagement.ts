/**
 * Who creates and administers an application's tenants.
 */
export enum TenantManagement {
    /**
     * The system: every user gets a tenant of its own at creation, and every row the user writes is
     * isolated in it.
     */
    SYSTEM = 'SYSTEM',
    /**
     * Tenant administrators: a customer signing up, or the organization in the portal, creates a tenant;
     * its administrator decides who joins and what they hold, within what the application allows. An
     * application whose administrators create no tenant has users with none, who share its data.
     */
    TENANT_ADMINISTRATORS = 'TENANT_ADMINISTRATORS'
}
