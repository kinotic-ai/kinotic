import type { Grant, Resource, RoleDefinition } from '@kinotic-ai/management-api'

/** The resource types of an organization's tree, each contained in the one before it. */
export const RESOURCE_TYPES = ['organization', 'application', 'project', 'entity_definition'] as const

export type ResourceType = typeof RESOURCE_TYPES[number]

const TYPE_LABELS: Record<string, string> = {
  organization: 'Organization',
  application: 'Application',
  project: 'Project',
  entity_definition: 'Entity definition'
}

/** A permission of the model, split into the type it is about and its short name. */
export interface PermissionName {
  /** The model name, such as project_can_edit. */
  name: string
  type: string
  /** The short name, such as can_edit. */
  permission: string
}

/** The readable name of a resource type: entity_definition is Entity definition. */
export function typeLabel(type: string): string {
  return TYPE_LABELS[type] ?? humanize(type)
}

/** Splits a model permission name, type_can_x, at its can_: entity_definition_can_view is about entity_definition. */
export function splitPermission(name: string): PermissionName {
  const at = name.indexOf('_can_')
  return at < 0
      ? { name, type: '', permission: name }
      : { name, type: name.substring(0, at), permission: name.substring(at + 1) }
}

/** The readable name of a permission's short name: can_view is View, can_manage_members is Manage members. */
export function permissionLabel(permission: string): string {
  return humanize(permission.startsWith('can_') ? permission.substring(4) : permission)
}

/** The types a resource of the given type contains, itself first; every type for an unknown one. */
export function typesWithin(type: string): string[] {
  const at = RESOURCE_TYPES.indexOf(type as ResourceType)
  return at < 0 ? [...RESOURCE_TYPES] : RESOURCE_TYPES.slice(at)
}

/** Whether a grant of the role on a resource of the type gives anything: a permission of the type or of one inside it. */
export function roleFits(role: RoleDefinition, type: string): boolean {
  const within = typesWithin(type)
  return role.permissions.some(permission => within.includes(splitPermission(permission).type))
}

/** Whether two resources are the same node. */
export function sameResource(a: Resource, b: Resource): boolean {
  return a.type === b.type && a.id === b.id
}

/**
 * The portal path of the Access page of a resource. A project's page sits under its application and an entity
 * definition's under both, so the ids of the ancestors the caller is looking at are needed to build them; null
 * when they are not known.
 */
export function accessPath(resource: Resource, context: { applicationId?: string, projectId?: string }): string | null {
  let ret: string | null
  const application = context.applicationId ? `/application/${encodeURIComponent(context.applicationId)}` : null
  switch (resource.type) {
    case 'organization':
      ret = '/organization-settings?tab=access'
      break
    case 'application':
      ret = `/application/${encodeURIComponent(resource.id)}/access`
      break
    case 'project':
      ret = application ? `${application}/project/${encodeURIComponent(resource.id)}/access` : null
      break
    case 'entity_definition':
      ret = application && context.projectId
          ? `${application}/project/${encodeURIComponent(context.projectId)}/entities/${encodeURIComponent(resource.id)}?tab=access`
          : null
      break
    default:
      ret = null
  }
  return ret
}

/** Whether a failed request was refused by the gateway's authorization check. */
export function isAuthorizationError(err: unknown): boolean {
  const message = err instanceof Error ? err.message : String(err)
  return message.includes('Not authorized') || message.includes('AuthorizationException')
}

/** The grants made on the resource itself, in the order listed. */
export function grantsOn(grants: Grant[], resource: Resource): Grant[] {
  return grants.filter(grant => sameResource(grant.resource, resource))
}

/** The grants reaching the resource from an ancestor, in the order listed: nearest ancestor first. */
export function grantsAbove(grants: Grant[], resource: Resource): Grant[] {
  return grants.filter(grant => !sameResource(grant.resource, resource))
}

function humanize(name: string): string {
  const words = name.split('_').filter(word => word.length > 0).join(' ')
  return words.charAt(0).toUpperCase() + words.substring(1)
}
