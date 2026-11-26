<template>
  <div class="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50/70 px-4 py-3.5 dark:border-red-500/30 dark:bg-red-500/10"
       role="alert">
    <span :class="['flex h-8 w-8 shrink-0 items-center justify-center rounded-lg', TINTS.red]">
      <CircleAlert :size="16" :stroke-width="1.75" aria-hidden="true" />
    </span>
    <div class="min-w-0 flex-1">
      <p class="text-sm font-semibold text-red-800 dark:text-red-300">{{ title }}</p>
      <p class="mt-0.5 break-words text-[0.8125rem] text-red-700/90 dark:text-red-300/90">{{ message }}</p>
    </div>
    <div v-if="$slots.action" class="flex shrink-0 items-center self-center whitespace-nowrap text-sm font-medium text-red-700 dark:text-red-300">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { CircleAlert } from '@lucide/vue'
import { TINTS } from '@kinotic-ai/frontend-common'

/**
 * Says that a project's last deployment failed and why, with an optional `action` slot for a link
 * to where the failure can be looked into.
 */
withDefaults(defineProps<{
  /** The deployment's `failureMessage`. */
  message: string
  title?: string
}>(), {
  title: 'The last deployment failed'
})
</script>
