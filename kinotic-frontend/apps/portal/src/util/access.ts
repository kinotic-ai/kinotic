import type { Grant, Resource, RoleDefinition } from '@kinotic-ai/management-api'

/**
 * The home types of the built-in roles, outermost first: a type is above another when a role homed at the other may
 * be granted on it, so the fewer types a home's roles may be granted on, the higher it sits.
 */
export function homeTypes(roles: RoleDefinition[]): string[] {
  const reach = new Map<string, number>()
  for (const role of roles.filter(role => role.builtIn)) {
    reach.set(role.resourceType, role.grantableOn.length)
  }
  return [...reach.keys()].sort((a, b) => reach.get(a)! - reach.get(b)! || a.localeCompare(b))
}

/** The home types whose roles a role homed at the given type may bundle the permissions of: the type and those inside it. */
export function typesWithin(home: string, roles: RoleDefinition[]): string[] {
  return homeTypes(roles).filter(type => roles.some(role => role.builtIn && role.resourceType === type && role.grantableOn.includes(home)))
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

/** The grants made on the resource itself, in the order listed. */
export function grantsOn(grants: Grant[], resource: Resource): Grant[] {
  return grants.filter(grant => sameResource(grant.resource, resource))
}

/** The grants reaching the resource from an ancestor, in the order listed: nearest ancestor first. */
export function grantsAbove(grants: Grant[], resource: Resource): Grant[] {
  return grants.filter(grant => !sameResource(grant.resource, resource))
}
