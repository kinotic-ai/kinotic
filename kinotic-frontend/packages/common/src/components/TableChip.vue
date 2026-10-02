<template>
  <RouterLink v-if="to" :to="to" :class="[CHIP_CLASS, LINK_CLASS]" @click.stop>
    <component v-if="icon" :is="icon" :size="13" :stroke-width="1.75" aria-hidden="true" />
    <slot />
  </RouterLink>
  <span v-else :class="CHIP_CLASS">
    <component v-if="icon" :is="icon" :size="13" :stroke-width="1.75" aria-hidden="true" />
    <slot />
  </span>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { RouterLink } from 'vue-router'

/**
 * A small grey chip for a table cell: an optional icon before the slot's content, such as a count
 * ("2 projects") or a label. With a route it is a link, and clicking it does not also click the row.
 */
defineProps<{
  icon?: Component
  to?: string
}>()

const CHIP_CLASS = 'inline-flex items-center gap-1.5 whitespace-nowrap rounded-md bg-surface-100 px-2 py-0.5 font-sans text-xs font-medium tabular-nums text-surface-700 dark:bg-surface-800 dark:text-surface-200'
const LINK_CLASS = 'transition-colors hover:bg-surface-200 hover:text-surface-950 dark:hover:bg-surface-700 dark:hover:text-surface-0'
</script>
