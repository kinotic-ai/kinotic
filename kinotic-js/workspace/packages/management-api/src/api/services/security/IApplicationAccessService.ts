import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { AccessExplanation } from '@/api/model/security/AccessExplanation'
import type { Grant } from '@/api/model/security/Grant'
import type { Resource } from '@/api/model/security/Resource'
import type { RoleDefinition } from '@/api/model/security/RoleDefinition'
import type { Subject } from '@/api/model/security/Subject'

/**
 * The access control of one application's users, in the application's own store: the roles its users and
 * machines can be granted, and the grants made on the application or on one of its tenants, which reach every
 * row of the application's entity definitions inside. Every function names the application, which must belong
 * to the caller's organization, and a subject named must be one of the application's own users or machines.
 */
export interface IApplicationAccessService {

    /**
     * The built-in roles the application's model defines: a viewer, an editor and an admin of each entity
     * definition's rows, and an admin of a tenant and of the application, each with the permissions it bundles.
     */
    findRoles(applicationId: string): Promise<RoleDefinition[]>

    /**
     * The grants that reach a resource: those made on it, then those made on the application when the resource
     * is one of its tenants, each naming the resource it was made on.
     * @param resource the application itself, or one of its tenants
     */
    findGrants(applicationId: string, resource: Resource): Promise<Grant[]>

    /**
     * Grants a role to a user or a machine of the application on the application or on one of its tenants: a
     * grant on a tenant reaches the rows of that tenant, a grant on the application the rows of every tenant.
     * @param subject a user naming the identity id of one of the application's users or machines
     * @param roleId a built-in role's id, such as invoice.editor or tenant.admin
     */
    grant(applicationId: string, subject: Subject, roleId: string, resource: Resource): Promise<Grant>

    /** Revokes a grant where it was made. */
    revoke(applicationId: string, resource: Resource, grantId: string): Promise<void>

    /**
     * Whether a user or a machine of the application holds a permission on a resource, and the grants it holds
     * the permission through.
     * @param permission the permission's model name, such as invoice_can_read
     */
    explain(applicationId: string, subject: Subject, permission: string, resource: Resource): Promise<AccessExplanation>

}

export class ApplicationAccessService implements IApplicationAccessService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.security.ApplicationAccessService`)
    }

    public findRoles(applicationId: string): Promise<RoleDefinition[]> {
        return this.serviceProxy.invoke('findRoles', [applicationId])
    }

    public findGrants(applicationId: string, resource: Resource): Promise<Grant[]> {
        return this.serviceProxy.invoke('findGrants', [applicationId, resource])
    }

    public grant(applicationId: string, subject: Subject, roleId: string, resource: Resource): Promise<Grant> {
        return this.serviceProxy.invoke('grant', [applicationId, subject, roleId, resource])
    }

    public revoke(applicationId: string, resource: Resource, grantId: string): Promise<void> {
        return this.serviceProxy.invoke('revoke', [applicationId, resource, grantId])
    }

    public explain(applicationId: string, subject: Subject, permission: string, resource: Resource): Promise<AccessExplanation> {
        return this.serviceProxy.invoke('explain', [applicationId, subject, permission, resource])
    }

}
