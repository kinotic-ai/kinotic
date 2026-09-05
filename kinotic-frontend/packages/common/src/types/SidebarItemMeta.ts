/**
 * Declares a route as an entry in an auto-generated sidebar. The sidebar for a group is
 * built by collecting every route whose meta carries a {@link SidebarItemMeta} with that
 * group, ordered by {@link order}. Dynamic path params are substituted from the current
 * route. Routes that should display a group's sidebar without being an item themselves
 * (detail/editor children) declare {@code meta.sidebarGroup} instead.
 */
export interface SidebarItemMeta {
  /** Which sidebar this item belongs to (e.g. 'organization', 'application'). */
  group: string
  label: string
  icon: string
  /** Position within the group, ascending. */
  order: number
  /** Optional section heading the item renders under. */
  section?: string
}
