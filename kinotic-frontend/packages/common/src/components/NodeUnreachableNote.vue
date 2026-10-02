<template>
  <span class="inline-flex items-center gap-1 whitespace-nowrap text-[11px] font-medium text-amber-700 dark:text-amber-300"
        v-tooltip.top="message || undefined">
    <TriangleAlert :size="12" :stroke-width="2" class="shrink-0" aria-hidden="true" />
    Node unreachable
  </span>
</template>

<script setup lang="ts">
import { TriangleAlert } from '@lucide/vue'

/**
 * A warning beside a workload's status that the node running it has stopped answering, so the
 * status is the last one it reported; the message, when given, shows on hover.
 */
defineProps<{
  message?: string | null
}>()
</script>
