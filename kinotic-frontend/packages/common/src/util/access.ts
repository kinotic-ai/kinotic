/** A permission of the model, split into the type it is about and its short name. */
export interface PermissionName {
  /** The model name, such as project_can_edit. */
  name: string
  type: string
  /** The short name, such as can_edit. */
  permission: string
}

const TYPE_LABELS: Record<string, string> = {
  platform: 'Platform',
  organization: 'Organization',
  application: 'Application',
  project: 'Project',
  entity_definition: 'Entity definition',
  vm_node: 'Worker node'
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

/** Whether a failed request was refused by the gateway's authorization check. */
export function isAuthorizationError(err: unknown): boolean {
  const message = err instanceof Error ? err.message : String(err)
  return message.includes('Not authorized') || message.includes('AuthorizationException')
}

function humanize(name: string): string {
  const words = name.split('_').filter(word => word.length > 0).join(' ')
  return words.charAt(0).toUpperCase() + words.substring(1)
}
