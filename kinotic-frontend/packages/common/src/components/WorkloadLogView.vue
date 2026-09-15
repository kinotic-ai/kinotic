<template>
  <div class="flex flex-col gap-3">
    <div class="flex flex-wrap items-center gap-3">
      <ToggleButton
        v-model="following"
        on-label="Following"
        off-label="Follow"
        on-icon="pi pi-pause"
        off-icon="pi pi-play"
        size="small"
        :disabled="span === CUSTOM_SPAN"
      />
      <SelectButton v-model="span" :options="spanOptions" option-label="label" option-value="value"
                    :allow-empty="false" size="small" />
      <Select v-model="limit" :options="LIMIT_OPTIONS" option-label="label" option-value="value" size="small" />
      <Button label="Reload" icon="pi pi-refresh" severity="secondary" outlined size="small"
              :loading="loadingHistory" @click="loadHistory" />
      <span class="text-xs text-muted-color">{{ lineCountText }}</span>
      <Message v-if="error" severity="error" :closable="false" class="flex-1">{{ error }}</Message>
    </div>
    <div v-if="span === CUSTOM_SPAN" class="flex flex-wrap items-center gap-3">
      <DatePicker v-model="customStart" show-time hour-format="24" size="small" placeholder="From" />
      <span class="text-xs text-muted-color">to</span>
      <DatePicker v-model="customEnd" show-time hour-format="24" size="small" placeholder="To" />
      <Button label="Apply" size="small" :disabled="resolveRange() === null" :loading="loadingHistory"
              @click="loadHistory" />
    </div>
    <div v-if="lines.length === 0" class="h-[60vh] p-3 rounded-md bg-surface-950 text-surface-400 font-mono text-xs">
      <span v-if="!loadingHistory">No log entries {{ rangeDescription }}</span>
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
import { computed, nextTick, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import Button from 'primevue/button'
import DatePicker from 'primevue/datepicker'
import Message from 'primevue/message'
import Select from 'primevue/select'
import SelectButton from 'primevue/selectbutton'
import ToggleButton from 'primevue/togglebutton'
import VirtualScroller from 'primevue/virtualscroller'

import { Kinotic } from '@kinotic-ai/core'
import { WorkloadStatus, type Workload } from '@kinotic-ai/management-api'
import DatetimeUtil from '../util/DatetimeUtil'
import { errorMessage, parseJsonBytes } from '../util/helpers'
import type { TimeRange } from './telemetry/TimeRange'
import { TIME_RANGE_PRESETS, rangeEndingNow } from './telemetry/telemetryApi'

const props = defineProps<{
  workloadId: string
  /**
   * The workload's record, when the caller has it. A workload whose run has ended opens on
   * the span of that run rather than the last hour, and does not follow, since nothing more
   * is coming; the run is also offered as a span for a workload still running.
   */
  workload?: Workload
}>()

/** The span option that reveals the absolute range pickers. */
const CUSTOM_SPAN = 'custom'
/** The span option covering the workload's run, from its creation to its end or to now. */
const RUN_SPAN = 'run'
/** A preset's milliseconds, or one of the named spans. */
type Span = number | typeof CUSTOM_SPAN | typeof RUN_SPAN

// Log lines carry the VM's clock and ship after the fact, so the run's span reaches a little past both ends
const RUN_MARGIN_MS = 60_000

const PRESET_OPTIONS: { label: string; value: Span; description: string }[] =
    TIME_RANGE_PRESETS.map(preset => ({ label: preset.shortLabel, value: preset.ms, description: `in the ${preset.label.toLowerCase()}` }))
const RUN_OPTION = { label: 'Whole run', value: RUN_SPAN, description: 'over the workload\'s run' }
const CUSTOM_OPTION = { label: 'Custom', value: CUSTOM_SPAN, description: 'in the selected range' }
// Loki rejects a limit above its max_entries_limit_per_query, 5000 unless configured higher
const LIMIT_OPTIONS = [500, 1000, 2000, 5000].map(value => ({ value, label: `${value.toLocaleString()} lines` }))
// The VirtualScroller keeps the DOM viewport-sized regardless of buffer length, so the
// cap only bounds heap and the per-frame concat cost of a long-running tail.
const MAX_LINES = 25_000
/** Fixed row height the VirtualScroller positions rows by; rows must render at exactly this height. */
const LINE_HEIGHT_PX = 20
const DAY_MS = 24 * 60 * 60_000

interface LogLine {
  ts: number
  line: string
}

function isFinished(workload: Workload | undefined): boolean {
  return workload?.status === WorkloadStatus.STOPPED || workload?.status === WorkloadStatus.FAILED
}

const spanOptions = computed(() => props.workload ? [...PRESET_OPTIONS, RUN_OPTION, CUSTOM_OPTION] : [...PRESET_OPTIONS, CUSTOM_OPTION])

// shallowRef: lines are immutable once parsed, so per-line reactive proxies buy nothing
const lines = shallowRef<LogLine[]>([])
const following = ref(!isFinished(props.workload))
const span = ref<Span>(isFinished(props.workload) ? RUN_SPAN : TIME_RANGE_PRESETS[1]!.ms)
const limit = ref(1000)
const customStart = ref<Date | null>(null)
const customEnd = ref<Date | null>(null)
// The range the buffer was loaded for; a preset resolves against the clock at load time
const loadedRange = ref<TimeRange | null>(null)
const limitReached = ref(false)
const loadingHistory = ref(false)
const error = ref<string | null>(null)
const scroller = ref<InstanceType<typeof VirtualScroller> | null>(null)
// Auto-scroll only while the user is at the bottom; scrolling up pins the view in place
let pinnedToBottom = true
let tailSubscription: { unsubscribe(): void } | null = null

const rangeDescription = computed(() => {
  let ret: string
  const range = loadedRange.value
  if (span.value === CUSTOM_SPAN && range) {
    ret = `between ${DatetimeUtil.formatEpochDateTime(range.start)} and ${DatetimeUtil.formatEpochDateTime(range.end)}`
  } else {
    ret = spanOptions.value.find(option => option.value === span.value)!.description
  }
  return ret
})

const lineCountText = computed(() => {
  const count = `${lines.value.length} lines`
  return limitReached.value ? `${count} · newest ${limit.value.toLocaleString()} in range` : count
})

// A range wider than a day, or crossing midnight, needs the date on each row to read
const showDate = computed(() => {
  const range = loadedRange.value
  return range !== null
      && (range.end - range.start > DAY_MS
          || new Date(range.start).toDateString() !== new Date(range.end).toDateString())
})

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

/** The range the current selection asks for, or null while a custom range is incomplete or inverted. */
function resolveRange(): TimeRange | null {
  let ret: TimeRange | null
  if (typeof span.value === 'number') {
    ret = rangeEndingNow(span.value)
  } else if (span.value === RUN_SPAN) {
    ret = runRange()
  } else if (customStart.value && customEnd.value && customStart.value < customEnd.value) {
    ret = { start: customStart.value.getTime(), end: customEnd.value.getTime() }
  } else {
    ret = null
  }
  return ret
}

// From the workload's creation to its last status change once it has ended, or to now while it runs
function runRange(): TimeRange {
  const workload = props.workload
  const created = DatetimeUtil.toEpochMillis(workload?.created ?? null)
  const updated = DatetimeUtil.toEpochMillis(workload?.updated ?? null)
  const start = created ?? Date.now() - TIME_RANGE_PRESETS[1]!.ms
  const end = isFinished(workload) && updated !== null ? updated + RUN_MARGIN_MS : Date.now()
  return { start: start - RUN_MARGIN_MS, end }
}

async function loadHistory() {
  const range = resolveRange()
  if (range === null) {
    return
  }
  loadingHistory.value = true
  error.value = null
  try {
    const bytes = await Kinotic.logs.history({
      workloadId: props.workloadId,
      start: range.start,
      end: range.end,
      limit: limit.value
    })
    // Raw Loki query_range response: {status, data: {result: [{stream, values}]}}
    const body = parseJsonBytes(bytes)
    lines.value = parseStreams(body?.data?.result).sort((a, b) => a.ts - b.ts)
    loadedRange.value = range
    // Loki answers newest-first up to the limit, so a full page means the range holds more
    limitReached.value = lines.value.length >= limit.value
    scrollToBottom()
  } catch (err) {
    error.value = errorMessage(err, 'Failed to load log history')
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
      const frame = parseJsonBytes(bytes)
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
      error.value = errorMessage(err, 'Log tail disconnected')
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

watch(span, selected => {
  if (selected === CUSTOM_SPAN) {
    // A live tail appended to a historical window would misread as part of it
    following.value = false
    // Seed the pickers with the range on screen so the user adjusts rather than starts blank
    const seed = loadedRange.value ?? rangeEndingNow(TIME_RANGE_PRESETS[1]!.ms)
    customStart.value = new Date(seed.start)
    customEnd.value = new Date(seed.end)
  } else {
    loadHistory()
  }
})

watch(limit, loadHistory)

// A workload that ends while on screen has nothing more to tail; its run is what is left to read
watch(() => isFinished(props.workload), finished => {
  if (finished) {
    following.value = false
    span.value = RUN_SPAN
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
  return showDate.value
      ? new Date(epochMillis).toLocaleString('en-US', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).replace(',', '')
      : DatetimeUtil.formatTime(epochMillis)
}
</script>
