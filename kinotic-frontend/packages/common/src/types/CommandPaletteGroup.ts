import type { CommandPaletteEntry } from './CommandPaletteEntry'

/** A titled section of the command palette, which also gets a filter tab of its own. */
export interface CommandPaletteGroup {
  label: string
  entries: CommandPaletteEntry[]
}
