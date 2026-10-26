/**
 * Sign-in with an email and a password at the login of one sign-in scope of an organization, a sign-in provider beside
 * its own OIDC configurations: the organization's own login when `applicationId` is null, an application's when
 * `tenantId` is null, else one tenant's of the application. A login takes a password only while a configuration of its
 * scope is enabled; a tenant's login also takes one while its application's is. A scope has at most one, whose id is
 * derived from the scope on save.
 */
export interface PasswordConfiguration {
    /** Derived from the scope on save, whatever was sent. */
    id: string | null
    /** The owning organization; set to the caller's on save, whatever was sent. */
    organizationId: string | null
    /** The application whose login, or whose tenant's login, offers the configuration; null for the organization's own. */
    applicationId: string | null
    /** The tenant of the application whose login offers the configuration; null unless it is a tenant's. */
    tenantId: string | null
    /** Whether the login takes an email and a password. */
    enabled: boolean
    /**
     * Whether a new person may sign up with an email and a password, the email verified by a link sent to it, from one
     * of the application's UIs. Taken by an application's own configuration only, and signs people up while the system
     * manages the application's tenants, which gives each new user a tenant of its own.
     */
    createUserOnSignIn: boolean
    created: string | null
    updated: string | null
}
