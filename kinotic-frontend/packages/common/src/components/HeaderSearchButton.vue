<template>
  <!-- Looks like a search box on wider screens and an icon button on small ones; either opens the search -->
  <button
    type="button"
    class="hidden h-9 w-60 items-center gap-2 rounded-md border border-surface-200 bg-surface-50 px-3 text-sm text-surface-500 transition-colors hover:border-surface-300 md:flex dark:border-surface-800 dark:bg-surface-900 dark:text-surface-400 dark:hover:border-surface-700"
    aria-label="Search"
    aria-keyshortcuts="Meta+K Control+K"
    @click="emit('open')"
  >
    <Search :size="16" :stroke-width="1.75" aria-hidden="true" />
    <span class="flex-1 text-left">Search</span>
    <kbd :class="KBD_CLASS">{{ SHORTCUT_MODIFIER }}</kbd>
    <kbd :class="KBD_CLASS">K</kbd>
  </button>
  <button
    type="button"
    class="flex h-9 w-9 items-center justify-center rounded-md text-surface-600 transition-colors hover:bg-surface-100 hover:text-surface-950 md:hidden dark:text-surface-300 dark:hover:bg-surface-800 dark:hover:text-surface-0"
    aria-label="Search"
    @click="emit('open')"
  >
    <Search :size="18" :stroke-width="1.75" aria-hidden="true" />
  </button>
</template>

<script setup lang="ts">
import { Search } from '@lucide/vue'

/** The header's search entry point, showing the ⌘K / Ctrl K shortcut; emits open when clicked. */
const emit = defineEmits<{
  (e: 'open'): void
}>()

const KBD_CLASS = 'rounded border border-surface-300 bg-surface-0 px-1.5 font-sans text-[11px] text-surface-600 dark:border-surface-700 dark:bg-surface-800 dark:text-surface-300'

// Apple keyboards label the shortcut's modifier ⌘; everywhere else it is Ctrl
const SHORTCUT_MODIFIER = /Mac|iPhone|iPad/.test(navigator.platform) ? '⌘' : 'Ctrl'
</script>
