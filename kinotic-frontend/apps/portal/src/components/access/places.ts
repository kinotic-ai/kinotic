import type { Resource } from '@kinotic-ai/management-api'

/** A published definition of the application, as the pickers name it. */
export interface DefinitionOption {
  id: string
  name: string
}

/** The types of the places a grant in an application's store is made on. */
const APPLICATION = 'application'
const TENANT = 'tenant'
const ENTITY_DEFINITION = 'entity_definition'
const TENANT_DEFINITION = 'tenant_definition'

/**
 * The place a tenant and a definition name, either or both left empty: the whole application, the tenant, the
 * definition's rows in every tenant, or the definition's rows within the tenant, which the store names
 * `<definition id>@<tenant id>`.
 */
export function placeOf(applicationId: string, tenantId: string, definitionId: string): Resource {
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

/** The tenant a place is in, or null for the whole application and for a definition's rows in every tenant. */
export function tenantOf(place: Resource): string | null {
  let ret: string | null = null
  if (place.type === TENANT) {
    ret = place.id
  } else if (place.type === TENANT_DEFINITION) {
    ret = place.id.substring(place.id.indexOf('@') + 1)
  }
  return ret
}

/** The definition a place names, or null for the whole application and for a tenant. */
export function definitionOf(place: Resource): string | null {
  let ret: string | null = null
  if (place.type === ENTITY_DEFINITION) {
    ret = place.id
  } else if (place.type === TENANT_DEFINITION) {
    ret = place.id.substring(0, place.id.indexOf('@'))
  }
  return ret
}

/**
 * Whether a grant made on one place reaches another: the place itself, the whole application, or the tenant or
 * the definition of a definition's rows within a tenant.
 */
export function reaches(made: Resource, place: Resource): boolean {
  const same = made.type === place.type && made.id === place.id
  const above = place.type === TENANT_DEFINITION
      && (made.type === TENANT && made.id === tenantOf(place) || made.type === ENTITY_DEFINITION && made.id === definitionOf(place))
  return same || made.type === APPLICATION || above
}

/**
 * How a place is named on a grant row and in a sentence: the whole application, the tenant, a definition's rows
 * in every tenant, or a definition's rows in one tenant, the definition by its name.
 */
export function placeLabel(place: Resource, definitions: DefinitionOption[]): string {
  let ret: string
  if (place.type === TENANT) {
    ret = `tenant ${place.id}`
  } else if (place.type === ENTITY_DEFINITION) {
    ret = `${definitionName(place.id, definitions)} rows in every tenant`
  } else if (place.type === TENANT_DEFINITION) {
    ret = `${definitionName(definitionOf(place) ?? '', definitions)} rows in tenant ${tenantOf(place)}`
  } else {
    ret = 'the whole application'
  }
  return ret
}

/** A definition's name, or the last segment of its id for one the pickers do not list. */
export function definitionName(id: string, definitions: DefinitionOption[]): string {
  return definitions.find(definition => definition.id === id)?.name ?? id.substring(id.lastIndexOf('.') + 1)
}
