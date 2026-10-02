import type { Component } from 'vue'

/** How the feed and the settings present a category, and whether it starts switched on. */
export interface CategoryInfo {
    label: string
    description: string
    group: 'Job runs' | 'Deployments'
    icon: Component
    /** Icon tile classes; red and amber only for failures and warnings. */
    tint: string
    failure: boolean
    defaultOn: boolean
}
