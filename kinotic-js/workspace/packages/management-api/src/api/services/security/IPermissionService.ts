import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import { FunctionalIterablePage, type IKinotic, type IServiceProxy, type IterablePage, type Page, type Pageable } from '@kinotic-ai/core'
import type { AccessExplanation } from '@/api/model/security/AccessExplanation'
import type { Grant } from '@/api/model/security/Grant'
import { Group } from '@/api/model/security/Group'
import type { Resource } from '@/api/model/security/Resource'
import type { RoleDefinition } from '@/api/model/security/RoleDefinition'
import type { Subject } from '@/api/model/security/Subject'
import { UserParticipantIdentity } from '@/api/model/security/UserParticipantIdentity'

/**
 * The access control of the caller's organization: the permissions its services declare, the roles that bundle
 * them, the groups of its members, and the grants of a role to a user or a group on the organization or on an
 * application, a project or an entity definition inside it. The backend derives the organization from the
 * authenticated participant, and every role, group, user and resource named must belong to it.
 */
export interface IPermissionService {

    /**
     * The catalog a custom role is defined from: the model names of the permissions, such as project_can_edit,
     * grouped by the resource type they are about.
     */
    findPermissions(): Promise<Record<string, string[]>>

    /** The built-in roles the model defines and the custom roles the organization has defined. */
    findRoles(): Promise<RoleDefinition[]>

    /**
     * Defines a custom role, or reshapes one: with no id a new role is created, with the id of a custom role
     * its name, description and permissions are replaced, which changes every grant of it at once. A built-in
     * role cannot be saved, and a permission the model does not have is refused.
     */
    saveRole(role: RoleDefinition): Promise<RoleDefinition>

    /** Deletes a custom role no grant holds. */
    deleteRole(roleId: string): Promise<void>

    /** Lists the organization's groups. */
    findGroups(pageable: Pageable): Promise<IterablePage<Group>>

    /** Creates a group, or renames one the organization has. */
    saveGroup(group: Group): Promise<Group>

    /** Deletes a group no grant holds. */
    deleteGroup(groupId: string): Promise<void>

    /** The members of a group. */
    findGroupMembers(groupId: string): Promise<UserParticipantIdentity[]>

    /** Puts a member of the organization in a group. */
    addGroupMember(groupId: string, userId: string): Promise<void>

    /** Takes a member out of a group. */
    removeGroupMember(groupId: string, userId: string): Promise<void>

    /**
     * Grants a role to a user or a group on a resource: the subject holds every permission the role bundles on
     * the resource and on everything inside it.
     */
    grant(subject: Subject, roleId: string, resource: Resource): Promise<Grant>

    /** Revokes a grant where it was made. */
    revoke(resource: Resource, grantId: string): Promise<void>

    /**
     * The grants that reach a resource: those made on it, then those made on each ancestor up to the
     * organization, each naming the resource it was made on.
     */
    findGrants(resource: Resource): Promise<Grant[]>

    /**
     * The ids of the resources of a type the caller holds a permission on, through any grant that reaches them.
     * @param type the resource type, such as project
     * @param permission the permission's short name, such as can_view
     */
    listAccessible(type: string, permission: string): Promise<string[]>

    /**
     * Whether a subject holds a permission on a resource, and the grants it holds the permission through.
     * @param permission the permission's short name, such as can_edit
     */
    explain(subject: Subject, permission: string, resource: Resource): Promise<AccessExplanation>

}

export class PermissionService implements IPermissionService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.security.PermissionService`)
    }

    public findPermissions(): Promise<Record<string, string[]>> {
        return this.serviceProxy.invoke('findPermissions', [])
    }

    public findRoles(): Promise<RoleDefinition[]> {
        return this.serviceProxy.invoke('findRoles', [])
    }

    public saveRole(role: RoleDefinition): Promise<RoleDefinition> {
        return this.serviceProxy.invoke('saveRole', [role])
    }

    public deleteRole(roleId: string): Promise<void> {
        return this.serviceProxy.invoke('deleteRole', [roleId])
    }

    public async findGroups(pageable: Pageable): Promise<IterablePage<Group>> {
        const page: Page<Group> = await this.serviceProxy.invoke('findGroups', [pageable])
        return new FunctionalIterablePage(pageable, page, (next: Pageable) => this.serviceProxy.invoke('findGroups', [next]))
    }

    public saveGroup(group: Group): Promise<Group> {
        return this.serviceProxy.invoke('saveGroup', [group])
    }

    public deleteGroup(groupId: string): Promise<void> {
        return this.serviceProxy.invoke('deleteGroup', [groupId])
    }

    public findGroupMembers(groupId: string): Promise<UserParticipantIdentity[]> {
        return this.serviceProxy.invoke('findGroupMembers', [groupId])
    }

    public addGroupMember(groupId: string, userId: string): Promise<void> {
        return this.serviceProxy.invoke('addGroupMember', [groupId, userId])
    }

    public removeGroupMember(groupId: string, userId: string): Promise<void> {
        return this.serviceProxy.invoke('removeGroupMember', [groupId, userId])
    }

    public grant(subject: Subject, roleId: string, resource: Resource): Promise<Grant> {
        return this.serviceProxy.invoke('grant', [subject, roleId, resource])
    }

    public revoke(resource: Resource, grantId: string): Promise<void> {
        return this.serviceProxy.invoke('revoke', [resource, grantId])
    }

    public findGrants(resource: Resource): Promise<Grant[]> {
        return this.serviceProxy.invoke('findGrants', [resource])
    }

    public listAccessible(type: string, permission: string): Promise<string[]> {
        return this.serviceProxy.invoke('listAccessible', [type, permission])
    }

    public explain(subject: Subject, permission: string, resource: Resource): Promise<AccessExplanation> {
        return this.serviceProxy.invoke('explain', [subject, permission, resource])
    }

}
