<template>
  <!-- overflow-clip clips to the rounded corners while leaving the section a non-scroll container,
       whose flex min-height is its content, so a page's flex column keeps the section at its
       content's height and the layout scrolls to its last rows. -->
  <section class="flex flex-col overflow-clip rounded-xl border border-surface-200 bg-surface-0 dark:border-surface-700 dark:bg-surface-800/30">
    <div class="flex items-start gap-3 border-b border-surface-200 px-5 py-4 dark:border-surface-700">
      <span :class="['flex h-9 w-9 shrink-0 items-center justify-center rounded-lg', tint]">
        <component :is="icon" :size="18" :stroke-width="1.75" aria-hidden="true" />
      </span>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2">
          <h2 class="text-sm font-semibold text-surface-950 dark:text-surface-0">{{ title }}</h2>
          <span v-if="count !== undefined" class="rounded-md bg-indigo-50 px-1.5 text-xs font-semibold tabular-nums text-indigo-600 ring-1 ring-indigo-200 dark:bg-indigo-500/15 dark:text-indigo-300 dark:ring-indigo-500/30">{{ count }}</span>
        </div>
        <p v-if="description" class="mt-0.5 max-w-[860px] text-xs leading-5 text-muted-color">{{ description }}</p>
      </div>
      <RouterLink v-if="linkTo" :to="linkTo"
                  class="flex shrink-0 items-center gap-1 whitespace-nowrap text-sm text-surface-500 transition-colors hover:text-surface-950 dark:text-surface-400 dark:hover:text-surface-0">
        {{ linkLabel }}
        <ArrowUpRight :size="14" :stroke-width="1.75" aria-hidden="true" />
      </RouterLink>
      <slot name="actions" />
    </div>
    <div class="dashboard-section__body flex-1">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import { ArrowUpRight } from '@lucide/vue'

/**
 * A titled card on a dashboard page: a tinted icon, the title with an optional count badge, an
 * optional line saying what the section shows and an optional link at the right, then the
 * section's content edge to edge beneath a divider, so a table's rows reach the card's sides
 * while its first and last columns line up with the header's icon and link.
 */
withDefaults(defineProps<{
  icon: Component
  /** Classes of the icon tile's colour, one of TINTS. */
  tint: string
  title: string
  count?: number
  description?: string
  /** Where the header's link leads, e.g. the full list the section samples. */
  linkTo?: RouteLocationRaw
  linkLabel?: string
}>(), {
  linkLabel: 'View all'
})
</script>

<style>
/* A table in a section runs edge to edge, so the card's border closes its last row */
.dashboard-section__body .p-datatable-tbody > tr:last-child > td {
  border-bottom-width: 0;
}

/* A CrudTable in a section drops its own frame and runs edge to edge like any section table; its
   search bar and paginator keep the 1.25rem inset the table's first and last columns use */
.dashboard-section__body .crud-table__toolbar {
  padding: 1rem 1.25rem 0;
}

.dashboard-section__body .crud-table__table-shell {
  border-width: 1px 0 0;
  border-radius: 0;
}

.dashboard-section__body .crud-table__table-shell + * {
  padding-inline: 1.25rem;
}

/* One type scale for a section's table: muted headers and every cell at the 13px CrudTable's
   rows use, so a cell's font (mono for names and ids, sans for prose) is its only variation */
.dashboard-section__body .p-datatable-thead > tr > th {
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--p-text-muted-color);
}

.dashboard-section__body .p-datatable-tbody > tr > td {
  font-size: 0.8125rem;
}

.dashboard-section__body .p-datatable .p-button {
  font-size: 0.8125rem;
}

/* Status tags read as part of the row's monospace data, as in CrudTable */
.dashboard-section__body .p-datatable .p-tag {
  font-family: var(--font-mono);
}
</style>
