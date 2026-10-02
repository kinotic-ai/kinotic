<template>
  <section class="flex flex-col overflow-hidden rounded-xl border border-surface-200 bg-surface-0 dark:border-surface-700 dark:bg-surface-800/30">
    <div class="flex items-start gap-3 border-b border-surface-200 px-5 py-4 dark:border-surface-700">
      <span :class="['flex h-9 w-9 shrink-0 items-center justify-center rounded-lg', tint]">
        <component :is="icon" :size="18" :stroke-width="1.75" aria-hidden="true" />
      </span>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2">
          <h2 class="text-sm font-semibold text-surface-950 dark:text-surface-0">{{ title }}</h2>
          <span v-if="count !== undefined" class="rounded-md bg-surface-100 px-1.5 text-xs font-medium tabular-nums text-surface-600 dark:bg-surface-800 dark:text-surface-300">{{ count }}</span>
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
    <div class="flex-1">
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
 * section's content edge to edge beneath a divider, so a table's rows reach the card's sides.
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
