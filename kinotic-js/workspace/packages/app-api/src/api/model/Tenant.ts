import type { Identifiable } from '@kinotic-ai/core'

/**
 * One tenant of an application: the slice of the application's SHARED rows a group of its users share, and the
 * object the application's store grants on. A tenant is created by a customer signing up, or by the application
 * for each user when it isolates them.
 */
export class Tenant implements Identifiable<string> {

    /** The record's id, <organizationId>.<applicationId>.<tenantId>. */
    public id!: string

    public organizationId!: string

    public applicationId!: string

    /**
     * The tenant's id within its application: what its users carry, the routing key of their shared rows, and
     * the id of the tenant object in the application's store.
     */
    public tenantId!: string

    /** The name the tenant's users know it by, such as the customer's company name. */
    public name: string = ''

    /** The user the tenant was created for: the customer who signed up, or the user it isolates. */
    public createdBy: string | null = null

    /**
     * The id of the OidcConfiguration the tenant signs its users in with, or null while the tenant has no
     * identity provider of its own.
     */
    public ssoConfigId: string | null = null

    /**
     * The role granted on the tenant to a user its identity provider signs in for the first time, such as
     * tenant.viewer; null grants membership alone.
     */
    public ssoRoleId: string | null = null

    public created: string | null = null

    public updated: string | null = null

}
