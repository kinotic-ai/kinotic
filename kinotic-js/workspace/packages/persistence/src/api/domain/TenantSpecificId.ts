/**
 * An identifier that can be used to find a Entity for a specific id and tenant
 */
export class TenantSpecificId {
    /**
     * The id of the entity
     */
    public entityId: string
    /**
     * The id of the tenant
     */
    public tenantId: string

    constructor(entityId: string, tenantId: string) {
        this.entityId = entityId
        this.tenantId = tenantId
    }
}
