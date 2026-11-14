import type { Resource } from '@kinotic-ai/management-api'

/** A published definition of the application, as the pickers name it. */
export interface DefinitionOption {
  id: string
  name: string
}

/** The types of the resources a grant in an application's store is made on. */
const APPLICATION = 'application'
const TENANT = 'tenant'
const ENTITY_DEFINITION = 'entity_definition'
const TENANT_DEFINITION = 'tenant_definition'

/**
 * The resource a tenant and a definition name, either or both left empty: the whole application, the tenant, the
 * definition's rows in every tenant, or the definition's rows within the tenant, which the store names
 * `<definition id>@<tenant id>`.
 */
export function resourceOf(applicationId: string, tenantId: string, definitionId: string): Resource {
  let ret: Resource
  if (tenantId.length > 0 && definitionId.length > 0) {
    ret = { type: TENANT_DEFINITION, id: `${definitionId}@${tenantId}` }
  } else if (tenantId.length > 0) {
    ret = { type: TENANT, id: tenantId }
  } else if (definitionId.length > 0) {
    ret = { type: ENTITY_DEFINITION, id: definitionId }
  } else {
    ret = { type: APPLICATION, id: applicationId }
  }
  return ret
}

/** The tenant a resource is in, or null for the whole application and for a definition's rows in every tenant. */
export function tenantOf(resource: Resource): string | null {
  let ret: string | null = null
  if (resource.type === TENANT) {
    ret = resource.id
  } else if (resource.type === TENANT_DEFINITION) {
    ret = resource.id.substring(resource.id.indexOf('@') + 1)
  }
  return ret
}

/** The definition a resource names, or null for the whole application and for a tenant. */
export function definitionOf(resource: Resource): string | null {
  let ret: string | null = null
  if (resource.type === ENTITY_DEFINITION) {
    ret = resource.id
  } else if (resource.type === TENANT_DEFINITION) {
    ret = resource.id.substring(0, resource.id.indexOf('@'))
  }
  return ret
}

/**
 * Whether a grant made on one resource reaches another: the resource itself, the whole application, or the
 * tenant or the definition of a definition's rows within a tenant.
 */
export function reaches(made: Resource, resource: Resource): boolean {
  const same = made.type === resource.type && made.id === resource.id
  const above = resource.type === TENANT_DEFINITION
      && (made.type === TENANT && made.id === tenantOf(resource) || made.type === ENTITY_DEFINITION && made.id === definitionOf(resource))
  return same || made.type === APPLICATION || above
}

/**
 * How a resource is named on a grant row and in a sentence: the whole application, the tenant, a definition's
 * rows in every tenant, or a definition's rows in one tenant, the definition by its name.
 */
export function resourceLabel(resource: Resource, definitions: DefinitionOption[]): string {
  let ret: string
  if (resource.type === TENANT) {
    ret = `tenant ${resource.id}`
  } else if (resource.type === ENTITY_DEFINITION) {
    ret = `${definitionName(resource.id, definitions)} rows in every tenant`
  } else if (resource.type === TENANT_DEFINITION) {
    ret = `${definitionName(definitionOf(resource) ?? '', definitions)} rows in tenant ${tenantOf(resource)}`
  } else {
    ret = 'the whole application'
  }
  return ret
}

/** A definition's name, or the last segment of its id for one the pickers do not list. */
export function definitionName(id: string, definitions: DefinitionOption[]): string {
  return definitions.find(definition => definition.id === id)?.name ?? id.substring(id.lastIndexOf('.') + 1)
}
