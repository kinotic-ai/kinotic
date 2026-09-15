<script setup lang="ts">
import Tooltip from 'primevue/tooltip'

const vTooltip = Tooltip

/**
 * The block at the top of the sidebar naming whose sidebar it is: a mark, the owner's name
 * and what kind of thing it is, and, for a nested scope, the way up to its parent.
 */
defineProps<{
  collapsed: boolean
  name: string
  kind: string
  /** primeicons class of the mark; {@link initials} takes its place when set. */
  icon?: string
  initials?: string
  /** Path of the parent scope; rendered as a back row above the mark when set. */
  backTo?: string
  backLabel?: string
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
      v-tooltip.right="{
        value: collapsed ? backLabel : null,
        pt: { text: '!bg-surface-900 !text-surface-0 !text-xs !font-medium', arrow: '!border-r-surface-900' }
      }"
    >
      <i class="pi pi-arrow-left" :style="{ fontSize: '12px' }" />
      <span v-if="!collapsed" class="truncate">{{ backLabel }}</span>
    </RouterLink>

    <div :class="['flex items-center gap-3', collapsed ? 'justify-center' : 'px-[10px] py-1']">
      <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-surface-100 text-surface-700 dark:bg-surface-800 dark:text-surface-200">
        <span v-if="initials" class="text-xs font-semibold">{{ initials }}</span>
        <i v-else :class="['pi', icon]" :style="{ fontSize: '15px' }" />
      </div>
      <div v-if="!collapsed" class="min-w-0">
        <div class="truncate text-sm font-semibold text-surface-950 dark:text-surface-0" :title="name">{{ name }}</div>
        <div class="app-sidebar-section-label truncate text-[10.5px] font-semibold uppercase tracking-[0.08em]">{{ kind }}</div>
      </div>
    </div>
  </div>
</template>
