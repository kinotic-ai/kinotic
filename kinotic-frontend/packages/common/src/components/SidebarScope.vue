<script setup lang="ts">
import type { Component } from 'vue'


/**
 * The block at the top of the sidebar naming whose sidebar it is: a mark, the owner's name
 * and what kind of thing it is, and, for a nested scope, the way up to its parent.
 */
defineProps<{
  collapsed: boolean
  name: string
  kind: string
  /** Line icon component of the mark; {@link initials} takes its place when set. */
  icon?: Component
  initials?: string
  /** Path of the parent scope; rendered as a back row above the mark when set. */
  backTo?: string
  backLabel?: string
}>()

const emit = defineEmits<{
  /** The mark was clicked: collapse or expand the sidebar. */
  (e: 'toggle'): void
}>()
</script>

<template>
  <div :class="['app-surface-divider mb-2 w-full border-b', collapsed ? 'pb-2' : 'pb-3']">
    <RouterLink
      v-if="backTo"
      :to="backTo"
      :class="[
        'flex items-center gap-2 rounded-lg text-xs text-surface-500 transition-colors hover:bg-surface-100 hover:text-surface-950 dark:text-surface-400 dark:hover:bg-surface-800 dark:hover:text-surface-0',
        collapsed ? 'mx-auto h-[30px] w-[38px] justify-center' : 'mb-1 px-[10px] py-1.5'
      ]"
      v-tooltip.right="collapsed ? backLabel : null"
    >
      <i class="pi pi-arrow-left" :style="{ fontSize: '12px' }" />
      <span v-if="!collapsed" class="truncate">{{ backLabel }}</span>
    </RouterLink>

    <div :class="['flex items-center gap-3', collapsed ? 'justify-center' : 'px-0.5 py-1']">
      <!-- The mark doubles as the sidebar toggle: hovering or focusing it swaps in the panel icon -->
      <button
        type="button"
        class="group relative flex h-9 w-9 shrink-0 cursor-pointer items-center justify-center rounded-lg bg-surface-100 text-surface-800 transition-colors hover:bg-surface-200 focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary-500 dark:bg-surface-800 dark:text-surface-100 dark:hover:bg-surface-700"
        :aria-label="collapsed ? 'Expand sidebar' : 'Collapse sidebar'"
        v-tooltip.right="'Toggle sidebar'"
        @click="emit('toggle')"
      >
        <span class="flex items-center justify-center group-hover:hidden group-focus-visible:hidden">
          <span v-if="initials" class="text-xs font-semibold">{{ initials }}</span>
          <component :is="icon" v-else :size="18" :stroke-width="1.75" aria-hidden="true" />
        </span>
        <svg class="hidden h-5 w-5 group-hover:block group-focus-visible:block" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <rect x="3" y="4" width="18" height="16" rx="3" />
          <path d="M9 4v16" />
        </svg>
      </button>
      <div v-if="!collapsed" class="min-w-0">
        <div class="truncate text-sm font-semibold text-surface-950 dark:text-surface-0" v-tooltip.top="name">{{ name }}</div>
        <div class="app-sidebar-section-label truncate text-[10.5px] font-semibold uppercase tracking-[0.08em]">{{ kind }}</div>
      </div>
    </div>
  </div>
</template>
