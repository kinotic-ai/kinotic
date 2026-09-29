<template>
  <span
    v-if="label"
    class="inline-flex items-center gap-1.5 rounded-full bg-teal-600/10 px-2.5 py-0.5 font-sans text-xs font-medium text-teal-900 dark:bg-teal-400/15 dark:text-teal-100"
    v-tooltip.top="exact"
  >
    <Clock :size="13" :stroke-width="2" aria-hidden="true" />
    {{ label }}
  </span>
  <span v-else class="text-muted-color">—</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Clock } from '@lucide/vue'
import { DatetimeUtil } from '@kinotic-ai/frontend-common'

/**
 * A timestamp as a soft pill with a clock, reading relatively ("Today", "3 days ago"); the exact
 * date and time show on hover. A missing timestamp renders as a dash.
 */
const props = defineProps<{
  date: Date | string | number | null | undefined
}>()

const label = computed(() => props.date ? DatetimeUtil.formatRelativeDate(props.date) : '')
const exact = computed(() => props.date ? new Date(props.date).toLocaleString() : undefined)
</script>
