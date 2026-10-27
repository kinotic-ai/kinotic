import { SYSTEM_API_ZONE,
         type AccessExplanation,
         type Grant,
         type RoleDefinition,
         type Subject } from '@kinotic-ai/management-api'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'

/**
 * Access control of the system itself: the roles the system users and machines can be
 * granted, and the grants made on the system, which reach every organization and node on it. A
 * subject named must be one of the system users or machines.
 */
export interface ISystemAccessService {

    /** The built-in roles the model defines, each with the permissions it bundles. */
    findRoles(): Promise<RoleDefinition[]>

    /** The grants made on the system. */
    findGrants(): Promise<Grant[]>

    /**
     * Grants a role to a system user or machine on the system: the subject holds every
     * permission the role bundles on the system and on everything on it.
     * @param subject a user naming a system user's or a machine's identity id
     * @param roleId a built-in role's id
     */
    grant(subject: Subject, roleId: string): Promise<Grant>

    /**
     * Revokes a grant made on the system, refusing the last grant of system.admin held by a system user who can sign in,
     * enabled and no longer pending.
     */
    revoke(grantId: string): Promise<void>

    /**
     * Whether a system user or machine holds a permission of the system, and the grants it
     * holds the permission through.
     * @param permission the permission's short name, such as can_manage_workloads
     */
    explain(subject: Subject, permission: string): Promise<AccessExplanation>

}

export class SystemAccessService implements ISystemAccessService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${SYSTEM_API_ZONE}~org.kinotic.system.api.services.SystemAccessService`)
    }

    public findRoles(): Promise<RoleDefinition[]> {
        return this.serviceProxy.invoke('findRoles', [])
    }

    public findGrants(): Promise<Grant[]> {
        return this.serviceProxy.invoke('findGrants', [])
    }

    public grant(subject: Subject, roleId: string): Promise<Grant> {
        return this.serviceProxy.invoke('grant', [subject, roleId])
    }

    public revoke(grantId: string): Promise<void> {
        return this.serviceProxy.invoke('revoke', [grantId])
    }

    public explain(subject: Subject, permission: string): Promise<AccessExplanation> {
        return this.serviceProxy.invoke('explain', [subject, permission])
    }

}
