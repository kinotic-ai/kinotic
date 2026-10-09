<template>
  <div class="overflow-hidden rounded-lg border border-surface-800 bg-surface-950">
    <div ref="scroller" class="max-h-80 overflow-y-auto py-1 font-mono text-[0.78rem] text-surface-200" @scroll="onScroll">
      <div v-for="(entry, index) in entries" :key="index" class="flex gap-3 pr-4 hover:bg-white/[0.04]">
        <span class="shrink-0 select-none border-r border-white/[0.06] px-3 tabular-nums text-surface-500">{{ DatetimeUtil.formatTime(entry.timestamp) }}</span>
        <span :class="['w-[5ch] shrink-0 uppercase', LEVEL_CLASS[entry.level]]">{{ entry.level }}</span>
        <span class="min-w-0 whitespace-pre-wrap break-words">{{ entry.message }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { TaskLogLevel, type TaskLogEntry } from '@kinotic-ai/management-api'
import { DatetimeUtil } from '@kinotic-ai/frontend-common'

/**
 * The lines a job run's task wrote to its log, oldest first. While the lines grow, the view stays
 * at the newest line unless the reader has scrolled up.
 */
const props = defineProps<{
  entries: TaskLogEntry[]
}>()

const LEVEL_CLASS: Record<TaskLogLevel, string> = {
  [TaskLogLevel.INFO]: 'text-sky-400',
  [TaskLogLevel.WARN]: 'text-amber-400',
  [TaskLogLevel.ERROR]: 'text-red-400'
}

const scroller = ref<HTMLElement | null>(null)
const atBottom = ref(true)

function onScroll(): void {
  const element = scroller.value
  if (element) {
    atBottom.value = element.scrollHeight - element.scrollTop - element.clientHeight < 8
  }
}

watch(() => props.entries.length, () => {
  if (atBottom.value) {
    nextTick(() => {
      const element = scroller.value
      if (element) {
        element.scrollTop = element.scrollHeight
      }
    })
  }
}, { immediate: true })
</script>
