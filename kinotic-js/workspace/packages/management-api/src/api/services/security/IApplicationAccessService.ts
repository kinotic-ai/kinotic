import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { AccessExplanation } from '@/api/model/security/AccessExplanation'
import type { Grant } from '@/api/model/security/Grant'
import type { Resource } from '@/api/model/security/Resource'
import type { RoleDefinition } from '@/api/model/security/RoleDefinition'
import type { Subject } from '@/api/model/security/Subject'

/**
 * The access control of one application's users, in the application's own store: the roles its users and
 * machines can be granted, and the grants made on the application, on one of its tenants, on one of its entity
 * definitions or on a definition within a tenant, which reach the rows below where they are made. A definition
 * within a tenant is named `<definition id>@<tenant id>`. Every function names the application, which must belong
 * to the caller's organization, and a subject named must be one of the application's own users or machines.
 */
export interface IApplicationAccessService {

    /**
     * The built-in roles the application's model defines: a viewer, an editor and an admin of a definition's rows,
     * the same for every definition, and an admin of a tenant and of the application, each with the permissions it
     * bundles.
     */
    findRoles(applicationId: string): Promise<RoleDefinition[]>

    /**
     * The grants that reach a resource: those made on it, then those made above it, on the tenant and the
     * definition of a definition within a tenant and on the application for everything but itself, each naming
     * the resource it was made on.
     * @param resource the application itself, one of its tenants, one of its entity definitions or a definition within a tenant
     */
    findGrants(applicationId: string, resource: Resource): Promise<Grant[]>

    /**
     * Grants a role to a user or a machine of the application: a grant on a definition within a tenant reaches
     * those rows alone, one on a tenant every definition's rows in that tenant, one on a definition its rows in
     * every tenant, and one on the application the rows of every definition in every tenant. A definition named
     * must be one the application holds.
     * @param subject a user naming the identity id of one of the application's users or machines
     * @param roleId a built-in role's id, such as entity_definition.editor or tenant.admin
     */
    grant(applicationId: string, subject: Subject, roleId: string, resource: Resource): Promise<Grant>

    /** Revokes a grant where it was made. */
    revoke(applicationId: string, resource: Resource, grantId: string): Promise<void>

    /**
     * Whether a user or a machine of the application holds a permission on a resource, and the grants it holds
     * the permission through.
     * @param permission the permission's model name, such as entity_definition_can_read
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
