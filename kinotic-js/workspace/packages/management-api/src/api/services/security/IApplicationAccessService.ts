import { MANAGEMENT_API_ZONE } from '@/api/Zones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { AccessExplanation } from '@/api/model/security/AccessExplanation'
import type { Grant } from '@/api/model/security/Grant'
import type { Resource } from '@/api/model/security/Resource'
import type { RoleDefinition } from '@/api/model/security/RoleDefinition'
import type { Subject } from '@/api/model/security/Subject'

/**
 * The access control of one application's users, in the application's own store: the roles its users and
 * machines can be granted, the application's own roles among them, and the grants made on the application, on one of its tenants, on one of its entity
 * definitions or on a definition within a tenant, which reach the rows below where they are made. A definition
 * within a tenant is named `<definition id>@<tenant id>`. Every function names the application, which must belong
 * to the caller's organization, and a subject named must be one of the application's own users or machines, or
 * one of its tenants, standing for every user of that tenant.
 *
 * A role of the application's own says what its holders may do with each entity and each service of the
 * application: Accountant reads and edits the rows of Invoice and reads those of InventoryItem. Reading, editing
 * and deleting the rows of a definition are each held on their own.
 */
export interface IApplicationAccessService {

    /**
     * The catalog a role of the application's own is defined from: the model names of the permissions of a tenant
     * and of everything in it, such as entity_definition_can_read, grouped by the resource type they are about.
     */
    findPermissions(applicationId: string): Promise<Record<string, string[]>>

    /**
     * The built-in roles the application's model defines, then the application's own: a viewer, an editor and an
     * admin of a definition's rows, the same for every definition, and an admin of a tenant and of the application,
     * each with the permissions it bundles.
     */
    findRoles(applicationId: string): Promise<RoleDefinition[]>

    /**
     * Defines a role of the application's own, or reshapes one: with no id a new role is created, with the id of
     * one its name, description and permissions are replaced, which changes every grant of it at once. The role is
     * homed at the tenant, so it is granted on a tenant or on the application. Its permissions are held on
     * everything such a grant reaches, and its definitionPermissions on the entity definitions they name alone.
     * A built-in role cannot be saved, and a permission a tenant's role cannot bundle or a definition the
     * application does not hold is refused.
     */
    saveRole(applicationId: string, role: RoleDefinition): Promise<RoleDefinition>

    /** Deletes a role of the application's own that no grant holds. */
    deleteRole(applicationId: string, roleId: string): Promise<void>

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
     * must be one the application holds, and a tenant named one the application has where its administrators
     * manage its tenants. An application's user is asked about within its own tenant alone, so a role granted to
     * every end user on the application is the role each user starts with in its own tenant.
     * @param subject a user naming the identity id of one of the application's users or machines, a tenant
     *                naming the id of one of its tenants, whose every user then holds the grant, or an
     *                application naming the application itself, whose every end user then holds it
     * @param roleId a built-in role's id, such as entity_definition.editor or tenant.admin, or the id of one of the application's own
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

    public findPermissions(applicationId: string): Promise<Record<string, string[]>> {
        return this.serviceProxy.invoke('findPermissions', [applicationId])
    }

    public findRoles(applicationId: string): Promise<RoleDefinition[]> {
        return this.serviceProxy.invoke('findRoles', [applicationId])
    }

    public saveRole(applicationId: string, role: RoleDefinition): Promise<RoleDefinition> {
        return this.serviceProxy.invoke('saveRole', [applicationId, role])
    }

    public deleteRole(applicationId: string, roleId: string): Promise<void> {
        return this.serviceProxy.invoke('deleteRole', [applicationId, roleId])
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
