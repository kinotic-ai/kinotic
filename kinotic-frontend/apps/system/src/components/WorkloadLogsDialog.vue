<template>
  <Dialog
    v-model:visible="visible"
    modal
    :header="`Logs — ${workloadName}`"
    :style="{ width: '70rem', maxWidth: '95vw' }"
    @hide="stopTail"
    @show="opened"
  >
    <div class="logs__toolbar">
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
      <span class="logs__count">{{ lines.length }} lines</span>
      <Message v-if="error" severity="error" :closable="false" class="logs__error">{{ error }}</Message>
    </div>

    <div ref="scroller" class="logs__scroller" @scroll="onScroll">
      <div v-if="lines.length === 0 && !loadingHistory" class="logs__empty">No log entries in the last hour</div>
      <div v-for="(entry, index) in lines" :key="index" class="logs__line">
        <span class="logs__timestamp">{{ formatTimestamp(entry.ts) }}</span>
        <span>{{ entry.line }}</span>
      </div>
    </div>
  </Dialog>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import ToggleButton from 'primevue/togglebutton'

import { Kinotic } from '@kinotic-ai/core'

const props = defineProps<{
  workloadId: string
  workloadName: string
}>()

const visible = defineModel<boolean>('visible', { required: true })

/** How far back the history query reaches, and its entry cap. */
const HISTORY_WINDOW_MS = 60 * 60 * 1000
const HISTORY_LIMIT = 1000
/** Oldest lines are dropped past this to keep a long-running tail from growing the DOM unbounded. */
const MAX_LINES = 2000

interface LogLine {
  ts: number
  line: string
}

const lines = ref<LogLine[]>([])
const following = ref(true)
const loadingHistory = ref(false)
const error = ref<string | null>(null)
const scroller = ref<HTMLElement | null>(null)
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
        lines.value = [...lines.value, ...fresh].slice(-MAX_LINES)
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

function opened() {
  lines.value = []
  error.value = null
  pinnedToBottom = true
  loadHistory()
  if (following.value) {
    startTail()
  }
}

watch(following, follow => {
  if (follow) {
    startTail()
  } else {
    stopTail()
  }
})

function onScroll() {
  const el = scroller.value
  if (el) {
    pinnedToBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 10
  }
}

function scrollToBottom() {
  if (pinnedToBottom) {
    nextTick(() => {
      const el = scroller.value
      if (el) {
        el.scrollTop = el.scrollHeight
      }
    })
  }
}

function formatTimestamp(epochMillis: number): string {
  return new Date(epochMillis).toLocaleTimeString('en-US', { hour12: false })
}
</script>

<style scoped>
.logs__toolbar {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.logs__count {
  font-size: 0.8rem;
  color: var(--p-text-muted-color);
}

.logs__error {
  flex: 1;
}

.logs__scroller {
  height: 60vh;
  overflow-y: auto;
  padding: 0.75rem;
  border-radius: 6px;
  background: var(--p-surface-950);
  color: var(--p-surface-200);
  font-family: monospace;
  font-size: 0.8rem;
}

.logs__empty {
  color: var(--p-surface-400);
}

.logs__line {
  display: flex;
  gap: 0.75rem;
  white-space: pre-wrap;
  word-break: break-all;
}

.logs__timestamp {
  flex-shrink: 0;
  color: var(--p-surface-500);
}
</style>
