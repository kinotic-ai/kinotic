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
      <slot name="actions" />
    </div>
    <div class="flex-1">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

/**
 * A titled card on a dashboard page: a tinted icon, the title with an optional count badge, an
 * optional line saying what the section shows, then the section's content edge to edge beneath a
 * divider, so a table's rows reach the card's sides.
 */
defineProps<{
  icon: Component
  /** Classes of the icon tile's colour, one of TINTS. */
  tint: string
  title: string
  count?: number
  description?: string
}>()
</script>
