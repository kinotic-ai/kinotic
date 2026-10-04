import { SYSTEM_API_ZONE,
         type AccessExplanation,
         type Grant,
         type RoleDefinition,
         type Subject } from '@kinotic-ai/management-api'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'

/**
 * Access control of the platform itself: the roles the platform's operators and machines can be
 * granted, and the grants made on the platform, which reach every organization and node on it. A
 * subject named must be one of the platform's own operators or machines.
 */
export interface ISystemAccessService {

    /** The built-in roles the model defines, each with the permissions it bundles. */
    findRoles(): Promise<RoleDefinition[]>

    /** The grants made on the platform. */
    findGrants(): Promise<Grant[]>

    /**
     * Grants a role to a platform operator or machine on the platform: the subject holds every
     * permission the role bundles on the platform and on everything on it.
     * @param subject a user naming an operator's or a machine's identity id
     * @param roleId a built-in role's id
     */
    grant(subject: Subject, roleId: string): Promise<Grant>

    /** Revokes a grant made on the platform. */
    revoke(grantId: string): Promise<void>

    /**
     * Whether a platform operator or machine holds a permission of the platform, and the grants it
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
