import type { Component } from 'vue'

/** One kind of thing a delete removes or keeps, with the names of the items it covers. */
export interface DeleteImpactRow {
    icon: Component
    label: string
    /** The individual items, by name. */
    items?: string[]
}
