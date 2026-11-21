import { Kinotic } from '@kinotic-ai/core'
import type { RoleDefinition } from '@kinotic-ai/management-api'

/** The roles a roles list and its editor work on: the organization's, or one application's. */
export interface RoleSource {
  /** The built-in roles and the custom ones. */
  findRoles(): Promise<RoleDefinition[]>
  /** The catalog a custom role is defined from: the model names of the permissions, by the type they are about. */
  findPermissions(): Promise<Record<string, string[]>>
  saveRole(role: RoleDefinition): Promise<RoleDefinition>
  deleteRole(roleId: string): Promise<void>
}

/** The roles of the application of the given id, or the organization's own when none is given. */
export function roleSourceOf(applicationId?: string): RoleSource {
  let ret: RoleSource
  if (applicationId) {
    ret = {
      findRoles: () => Kinotic.applicationAccess.findRoles(applicationId),
      findPermissions: () => Kinotic.applicationAccess.findPermissions(applicationId),
      saveRole: role => Kinotic.applicationAccess.saveRole(applicationId, role),
      deleteRole: roleId => Kinotic.applicationAccess.deleteRole(applicationId, roleId)
    }
  } else {
    ret = {
      findRoles: () => Kinotic.permissions.findRoles(),
      findPermissions: () => Kinotic.permissions.findPermissions(),
      saveRole: role => Kinotic.permissions.saveRole(role),
      deleteRole: roleId => Kinotic.permissions.deleteRole(roleId)
    }
  }
  return ret
}
