<template>
  <div class="flex flex-col gap-3">
    <div class="flex items-center gap-3">
      <ToggleButton
        v-model="following"
        on-label="Following"
        off-label="Follow"
        on-icon="pi pi-pause"
        off-icon="pi pi-play"
        size="small"
      />
      <Button label="Reload history" icon="pi pi-refresh" severity="secondary" outlined size="small"
              :loading="loadingHistory" @click="loadHistory" />
      <span class="text-xs text-muted-color">{{ lines.length }} lines</span>
      <Message v-if="error" severity="error" :closable="false" class="flex-1">{{ error }}</Message>
    </div>
    <div v-if="lines.length === 0" class="h-[60vh] p-3 rounded-md bg-surface-950 text-surface-400 font-mono text-xs">
      <span v-if="!loadingHistory">No log entries in the last hour</span>
    </div>
    <VirtualScroller
      v-else
      ref="scroller"
      :items="lines"
      :itemSize="LINE_HEIGHT_PX"
      class="h-[60vh] rounded-md bg-surface-950 text-surface-200 font-mono text-xs"
      @scroll="onScroll"
    >
      <template #item="{ item }">
        <div class="flex gap-3 whitespace-pre px-3 h-5 items-center">
          <span class="shrink-0 text-surface-500">{{ formatTimestamp(item.ts) }}</span>
          <span>{{ item.line }}</span>
        </div>
      </template>
    </VirtualScroller>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import ToggleButton from 'primevue/togglebutton'
import VirtualScroller from 'primevue/virtualscroller'

import { Kinotic } from '@kinotic-ai/core'

const props = defineProps<{
  workloadId: string
}>()

/** How far back the history query reaches, and its entry cap. */
const HISTORY_WINDOW_MS = 60 * 60 * 1000
const HISTORY_LIMIT = 1000
// The VirtualScroller keeps the DOM viewport-sized regardless of buffer length, so the
// cap only bounds heap and the per-frame concat cost of a long-running tail.
const MAX_LINES = 25_000
/** Fixed row height the VirtualScroller positions rows by; rows must render at exactly this height. */
const LINE_HEIGHT_PX = 20

interface LogLine {
  ts: number
  line: string
}

// shallowRef: lines are immutable once parsed, so per-line reactive proxies buy nothing
const lines = shallowRef<LogLine[]>([])
const following = ref(true)
const loadingHistory = ref(false)
const error = ref<string | null>(null)
const scroller = ref<InstanceType<typeof VirtualScroller> | null>(null)
// Auto-scroll only while the user is at the bottom; scrolling up pins the view in place
let pinnedToBottom = true
let tailSubscription: { unsubscribe(): void } | null = null

const decoder = new TextDecoder()

// Both Loki payloads carry entries as streams of [nanosecond-timestamp, line] tuples
function parseStreams(streams: Array<{ values?: [string, string][] }> | undefined): LogLine[] {
  const out: LogLine[] = []
  for (const stream of streams ?? []) {
    for (const [ns, line] of stream.values ?? []) {
      out.push({ ts: Number(ns) / 1_000_000, line })
    }
  }
  return out
}

async function loadHistory() {
  loadingHistory.value = true
  error.value = null
  try {
    const now = Date.now()
    const bytes = await Kinotic.logs.history({
      workloadId: props.workloadId,
      start: now - HISTORY_WINDOW_MS,
      end: now,
      limit: HISTORY_LIMIT
    })
    // Raw Loki query_range response: {status, data: {result: [{stream, values}]}}
    const body = JSON.parse(decoder.decode(bytes))
    lines.value = parseStreams(body?.data?.result).sort((a, b) => a.ts - b.ts)
    scrollToBottom()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load log history'
  } finally {
    loadingHistory.value = false
  }
}

function startTail() {
  if (tailSubscription) {
    return
  }
  tailSubscription = Kinotic.logs.tail(props.workloadId).subscribe({
    next: (bytes: Uint8Array) => {
      // Raw Loki tail WebSocket frame: {streams: [{stream, values}], dropped_entries?}
      const frame = JSON.parse(decoder.decode(bytes))
      const fresh = parseStreams(frame?.streams)
      if (fresh.length > 0) {
        const merged = lines.value.concat(fresh)
        lines.value = merged.length > MAX_LINES ? merged.slice(-MAX_LINES) : merged
        scrollToBottom()
      }
    },
    error: (err: unknown) => {
      tailSubscription = null
      following.value = false
      error.value = err instanceof Error ? err.message : 'Log tail disconnected'
    }
  })
}

function stopTail() {
  tailSubscription?.unsubscribe()
  tailSubscription = null
}

onMounted(() => {
  loadHistory()
  if (following.value) {
    startTail()
  }
})

onUnmounted(stopTail)

watch(following, follow => {
  if (follow) {
    startTail()
  } else {
    stopTail()
  }
})

function onScroll(event: Event) {
  const el = event.target as HTMLElement
  pinnedToBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 2 * LINE_HEIGHT_PX
}

function scrollToBottom() {
  if (pinnedToBottom) {
    nextTick(() => scroller.value?.scrollToIndex(lines.value.length - 1))
  }
}

function formatTimestamp(epochMillis: number): string {
  return new Date(epochMillis).toLocaleTimeString('en-US', { hour12: false })
}
</script>
