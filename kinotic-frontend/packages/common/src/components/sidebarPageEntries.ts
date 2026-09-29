import { markRaw } from 'vue'
import type { Router } from 'vue-router'
import type { CommandPaletteEntry } from '../types/CommandPaletteEntry'
import type { SidebarItemMeta } from '../types/SidebarItemMeta'

/**
 * The sidebar pages of the given groups that need no route params, so they open from anywhere,
 * as command palette entries in the order the groups are given, hinted with their group's label.
 * Each entry's run receives the page's path.
 */
export function sidebarPageEntries(router: Router,
                                   groupLabels: Record<string, string>,
                                   open: (path: string) => void): CommandPaletteEntry[] {
  const order = Object.keys(groupLabels)
  return router.getRoutes()
               .filter(record => {
                 const sidebar = record.meta?.sidebar as SidebarItemMeta | undefined
                 return sidebar && groupLabels[sidebar.group] && !record.path.includes(':')
               })
               .sort((a, b) => {
                 const left = a.meta.sidebar as SidebarItemMeta
                 const right = b.meta.sidebar as SidebarItemMeta
                 return order.indexOf(left.group) - order.indexOf(right.group) || left.order - right.order
               })
               .map(record => {
                 const sidebar = record.meta.sidebar as SidebarItemMeta
                 return {
                   key: `page:${record.path}`,
                   label: sidebar.label,
                   hint: groupLabels[sidebar.group],
                   icon: markRaw(sidebar.icon),
                   run: () => open(record.path)
                 }
               })
}
