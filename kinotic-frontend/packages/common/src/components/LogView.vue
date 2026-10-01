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
      <Select checkmark v-model="limit" :options="LIMIT_OPTIONS" option-label="label" option-value="value" size="small" />
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
    <div v-if="rows.length === 0" class="h-[60vh] p-3 rounded-md bg-surface-950 text-surface-400 font-mono text-xs">
      <span v-if="!loadingHistory">No log entries {{ rangeDescription }}</span>
    </div>
    <VirtualScroller
      v-else
      ref="scroller"
      :items="rows"
      :itemSize="LINE_HEIGHT_PX"
      class="h-[60vh] rounded-md bg-surface-950 text-surface-200 font-mono text-xs"
      @scroll="onScroll"
    >
      <template #item="{ item }">
        <!-- A continuation row keeps its entry's timestamp and level invisible so its text aligns under the first row's -->
        <div class="flex gap-3 whitespace-pre px-3 h-5 items-center">
          <span class="shrink-0 text-surface-500" :class="{ invisible: item.continuation }">{{ formatTimestamp(item.ts) }}</span>
          <span v-if="item.level" class="shrink-0 min-w-[5ch] uppercase" :class="{ invisible: item.continuation }"
                :style="{ color: LEVEL_COLORS[item.level as LogLevel] }">{{ item.level }}</span>
          <span><span v-for="(segment, i) in item.segments" :key="i" :style="segment.style">{{ segment.text }}</span></span>
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

import DatetimeUtil from '../util/DatetimeUtil'
import { errorMessage, parseJsonBytes } from '../util/helpers'
import type { LogSource } from './LogSource'
import type { TimeRange } from './telemetry/TimeRange'
import { TIME_RANGE_PRESETS, rangeEndingNow } from './telemetry/telemetryApi'
import type { WorkloadRun } from './WorkloadRun'

const props = defineProps<{
  /** The logs to show. */
  source: LogSource
  /**
   * The window a workload ran over, when the caller knows it. A run that has ended opens on
   * its own span rather than the last hour, and does not follow, since nothing more is coming;
   * the run is also offered as a span while it is still going.
   */
  run?: WorkloadRun
}>()

/** The span option that reveals the absolute range pickers. */
const CUSTOM_SPAN = 'custom'
/** The span option covering the workload's run, from its start to its end or to now. */
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
const MAX_ROWS = 25_000
/** Fixed row height the VirtualScroller positions rows by; rows must render at exactly this height. */
const LINE_HEIGHT_PX = 20
const DAY_MS = 24 * 60 * 60_000

// The 16 ANSI colors as a dark terminal renders them: normal 0-7, then bright 8-15
const ANSI_PALETTE = [
  '#000000', '#cd3131', '#0dbc79', '#e5e510', '#2472c8', '#bc3fbc', '#11a8cd', '#e5e5e5',
  '#666666', '#f14c4c', '#23d18b', '#f5f543', '#3b8eea', '#d670d6', '#29b8db', '#ffffff'
]
const ANSI_CUBE_STEPS = [0, 95, 135, 175, 215, 255]
// CSI sequences (SGR when the final byte is m), OSC sequences, and the remaining two-byte escapes
const ANSI_ESCAPE = /\x1b\[([0-9;:?]*)([@-~])|\x1b\][^\x07\x1b]*(?:\x07|\x1b\\)?|\x1b[@-Z\\-_]/g

// Loki's detected_level values, colored as Spring Boot's console colors its level column
const LEVEL_COLORS = {
  fatal: ANSI_PALETTE[1]!,
  critical: ANSI_PALETTE[1]!,
  error: ANSI_PALETTE[1]!,
  warn: ANSI_PALETTE[3]!,
  info: ANSI_PALETTE[2]!,
  debug: ANSI_PALETTE[2]!,
  trace: ANSI_PALETTE[2]!
}
type LogLevel = keyof typeof LEVEL_COLORS

/** One Loki entry; its text may span several lines. */
interface LogEntry {
  ts: number
  level: LogLevel | null
  text: string
}

/** A run of a line's text drawn in one ANSI style. */
interface StyledSegment {
  text: string
  style: string | undefined
}

/** One line of an entry's text, the unit the VirtualScroller lays out. */
interface LogRow {
  ts: number
  level: LogLevel | null
  /** True for every line of an entry after its first. */
  continuation: boolean
  segments: StyledSegment[]
}

/** The SGR attributes in effect at a point in an entry's text. */
interface AnsiStyle {
  fg: string | null
  bg: string | null
  bold: boolean
  faint: boolean
  italic: boolean
  underline: boolean
}

function hasEnded(run: WorkloadRun | undefined): boolean {
  return run !== undefined && run.finished !== null
}

const spanOptions = computed(() => props.run ? [...PRESET_OPTIONS, RUN_OPTION, CUSTOM_OPTION] : [...PRESET_OPTIONS, CUSTOM_OPTION])

// shallowRef: rows are immutable once parsed, so per-row reactive proxies buy nothing
const rows = shallowRef<LogRow[]>([])
// The entries behind rows; rows outnumber them by every extra line of a multi-line entry
const entryCount = ref(0)
const following = ref(!hasEnded(props.run))
const span = ref<Span>(hasEnded(props.run) ? RUN_SPAN : TIME_RANGE_PRESETS[1]!.ms)
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
  const count = `${entryCount.value} lines`
  return limitReached.value ? `${count} · newest ${limit.value.toLocaleString()} in range` : count
})

// A range wider than a day, or crossing midnight, needs the date on each row to read
const showDate = computed(() => {
  const range = loadedRange.value
  return range !== null
      && (range.end - range.start > DAY_MS
          || new Date(range.start).toDateString() !== new Date(range.end).toDateString())
})

// Both Loki payloads carry entries as streams of [nanosecond-timestamp, line] tuples. Loki merges an
// entry's structured metadata into its stream labels, which is where an OTLP-shipped log keeps the
// level Loki detected and the stack trace of the exception it was logged with.
function parseStreams(streams: Array<{ stream?: Record<string, string>; values?: [string, string][] }> | undefined): LogEntry[] {
  const out: LogEntry[] = []
  for (const { stream, values } of streams ?? []) {
    const detectedLevel = stream?.detected_level
    const level = detectedLevel !== undefined && Object.hasOwn(LEVEL_COLORS, detectedLevel) ? detectedLevel as LogLevel : null
    const stackTrace = stream?.exception_stacktrace
    for (const [ns, line] of values ?? []) {
      const message = line.replace(/\n+$/, '')
      out.push({ ts: Number(ns) / 1_000_000, level, text: stackTrace ? `${message}\n${stackTrace}` : message })
    }
  }
  return out
}

function toRows(entries: LogEntry[]): LogRow[] {
  const out: LogRow[] = []
  for (const entry of entries) {
    // A style carries across the entry's lines, as a terminal carries it across a newline
    const style = plainStyle()
    entry.text.split(/\r?\n/).forEach((line, i) => {
      out.push({ ts: entry.ts, level: entry.level, continuation: i > 0, segments: styleSegments(line, style) })
    })
  }
  return out
}

function plainStyle(): AnsiStyle {
  return { fg: null, bg: null, bold: false, faint: false, italic: false, underline: false }
}

// Splits a line at its ANSI escapes into styled runs, applying SGR escapes to style and dropping the rest
function styleSegments(line: string, style: AnsiStyle): StyledSegment[] {
  const out: StyledSegment[] = []
  let textStart = 0
  for (const match of line.matchAll(ANSI_ESCAPE)) {
    pushSegment(out, line.slice(textStart, match.index), style)
    if (match[2] === 'm') {
      applySgr(match[1]!, style)
    }
    textStart = match.index + match[0].length
  }
  pushSegment(out, line.slice(textStart), style)
  return out
}

function pushSegment(segments: StyledSegment[], text: string, style: AnsiStyle) {
  if (text.length > 0) {
    segments.push({ text, style: toCss(style) })
  }
}

function applySgr(params: string, style: AnsiStyle) {
  // An omitted parameter means 0, so a bare ESC[m is a reset
  const codes = params.split(';').map(code => code === '' ? 0 : Number(code))
  for (let i = 0; i < codes.length; i++) {
    const code = codes[i]!
    if (code === 0) {
      Object.assign(style, plainStyle())
    } else if (code === 1) {
      style.bold = true
    } else if (code === 2) {
      style.faint = true
    } else if (code === 3) {
      style.italic = true
    } else if (code === 4) {
      style.underline = true
    } else if (code === 22) {
      style.bold = false
      style.faint = false
    } else if (code === 23) {
      style.italic = false
    } else if (code === 24) {
      style.underline = false
    } else if (code >= 30 && code <= 37) {
      style.fg = ANSI_PALETTE[code - 30]!
    } else if (code === 39) {
      style.fg = null
    } else if (code >= 40 && code <= 47) {
      style.bg = ANSI_PALETTE[code - 40]!
    } else if (code === 49) {
      style.bg = null
    } else if (code >= 90 && code <= 97) {
      style.fg = ANSI_PALETTE[code - 90 + 8]!
    } else if (code >= 100 && code <= 107) {
      style.bg = ANSI_PALETTE[code - 100 + 8]!
    } else if (code === 38 || code === 48) {
      // 38;5;n picks from the 256-color table and 38;2;r;g;b is a 24-bit color; 48 sets the background alike
      let color: string | null = null
      if (codes[i + 1] === 5) {
        color = color256(codes[i + 2]!)
        i += 2
      } else if (codes[i + 1] === 2) {
        color = `rgb(${codes[i + 2]}, ${codes[i + 3]}, ${codes[i + 4]})`
        i += 4
      }
      if (code === 38) {
        style.fg = color
      } else {
        style.bg = color
      }
    }
  }
}

// The xterm 256-color table: the 16 ANSI colors, a 6x6x6 color cube, then a 24-step gray ramp
function color256(index: number): string | null {
  let ret: string | null
  if (!Number.isInteger(index) || index < 0 || index > 255) {
    ret = null
  } else if (index < 16) {
    ret = ANSI_PALETTE[index]!
  } else if (index < 232) {
    const cube = index - 16
    ret = `rgb(${ANSI_CUBE_STEPS[Math.floor(cube / 36)]}, ${ANSI_CUBE_STEPS[Math.floor(cube / 6) % 6]}, ${ANSI_CUBE_STEPS[cube % 6]})`
  } else {
    const gray = 8 + (index - 232) * 10
    ret = `rgb(${gray}, ${gray}, ${gray})`
  }
  return ret
}

function toCss(style: AnsiStyle): string | undefined {
  const declarations: string[] = []
  if (style.fg) {
    declarations.push(`color: ${style.fg}`)
  }
  if (style.bg) {
    declarations.push(`background-color: ${style.bg}`)
  }
  if (style.bold) {
    declarations.push('font-weight: bold')
  }
  if (style.faint) {
    declarations.push('opacity: 0.6')
  }
  if (style.italic) {
    declarations.push('font-style: italic')
  }
  if (style.underline) {
    declarations.push('text-decoration: underline')
  }
  return declarations.length > 0 ? declarations.join('; ') : undefined
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

// From the run's start to its end once it has ended, or to now while it is still going
function runRange(): TimeRange {
  const started = props.run?.started ?? null
  const finished = props.run?.finished ?? null
  const start = started ?? Date.now() - TIME_RANGE_PRESETS[1]!.ms
  const end = finished !== null ? finished + RUN_MARGIN_MS : Date.now()
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
    const bytes = await props.source.history(range.start, range.end, limit.value)
    // Raw Loki query_range response: {status, data: {result: [{stream, values}]}}
    const body = parseJsonBytes(bytes)
    const entries = parseStreams(body?.data?.result).sort((a, b) => a.ts - b.ts)
    rows.value = toRows(entries)
    entryCount.value = entries.length
    loadedRange.value = range
    // Loki answers newest-first up to the limit, so a full page means the range holds more
    limitReached.value = entries.length >= limit.value
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
  tailSubscription = props.source.tail().subscribe({
    next: (bytes: Uint8Array) => {
      // Raw Loki tail WebSocket frame: {streams: [{stream, values}], dropped_entries?}
      const frame = parseJsonBytes(bytes)
      const fresh = parseStreams(frame?.streams)
      if (fresh.length > 0) {
        const merged = rows.value.concat(toRows(fresh))
        const overflow = Math.max(0, merged.length - MAX_ROWS)
        const droppedEntries = merged.slice(0, overflow).filter(row => !row.continuation).length
        rows.value = overflow > 0 ? merged.slice(overflow) : merged
        entryCount.value += fresh.length - droppedEntries
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

// A run that ends while on screen has nothing more to tail; its span is what is left to read
watch(() => hasEnded(props.run), ended => {
  if (ended) {
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
    nextTick(() => scroller.value?.scrollToIndex(rows.value.length - 1))
  }
}

function formatTimestamp(epochMillis: number): string {
  return showDate.value
      ? new Date(epochMillis).toLocaleString('en-US', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).replace(',', '')
      : DatetimeUtil.formatTime(epochMillis)
}
</script>
