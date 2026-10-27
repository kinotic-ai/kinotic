<template>
  <span
    v-if="label"
    class="inline-flex items-center gap-1.5 whitespace-nowrap rounded-full bg-green-100 px-2.5 py-0.5 font-sans text-xs font-medium text-green-700 dark:bg-green-500/16 dark:text-green-300"
    v-tooltip.top="exact"
  >
    <Clock :size="13" :stroke-width="2" aria-hidden="true" />
    {{ label }}
  </span>
  <span v-else class="text-muted-color">—</span>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { Clock } from '@lucide/vue'
import DatetimeUtil from '../util/DatetimeUtil'

/**
 * A timestamp as a soft pill with a clock, reading to the minute ("5 minutes ago", "Today at 2:54 AM",
 * "Oct 16 at 3:10 PM") and kept current while shown; the exact date and time show on hover. A missing
 * timestamp renders as a dash.
 */
const props = defineProps<{
  date: Date | string | number | null | undefined
}>()

// keeps the minute-granular label current while the pill stays on screen
const now = ref(Date.now())
const ticker = setInterval(() => { now.value = Date.now() }, 30000)
onUnmounted(() => clearInterval(ticker))

const label = computed(() => {
  const relative = props.date ? DatetimeUtil.formatRelativeDate(props.date, now.value) : ''
  return relative.charAt(0).toUpperCase() + relative.slice(1)
})
const exact = computed(() => props.date ? new Date(props.date).toLocaleString() : undefined)
</script>
