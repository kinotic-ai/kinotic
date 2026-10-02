import type { Component } from 'vue'

/** One result the command palette lists and opens. */
export interface CommandPaletteEntry {
  /** Unique across every group of the palette. */
  key: string
  label: string
  /** Secondary text beside the label, e.g. an id. */
  hint?: string
  /** The line icon before the label; ignored when {@link tileIndex} is set. */
  icon?: Component
  /** Shows an InitialsTile of the label in this list position's color instead of an icon. */
  tileIndex?: number
  /** Marks an entry that opens a new tab rather than navigating inside the app. */
  external?: boolean
  run: () => void
}
