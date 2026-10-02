<template>
  <div class="flex flex-col gap-3">
    <div class="flex flex-wrap items-center gap-2">
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
      <div class="ml-auto flex items-center gap-2">
        <Select checkmark v-model="limit" :options="LIMIT_OPTIONS" option-label="label" option-value="value" size="small" />
        <Button icon="pi pi-refresh" severity="secondary" outlined size="small" aria-label="Reload"
                v-tooltip.top="'Reload'" :loading="loadingHistory" @click="refresh" />
      </div>
    </div>
    <div v-if="span === CUSTOM_SPAN"
         class="flex flex-wrap items-center gap-3 rounded-lg border border-surface-200 bg-surface-50 px-3 py-2 dark:border-surface-700 dark:bg-surface-800/50">
      <DatePicker v-model="customStart" show-time hour-format="24" size="small" placeholder="From" />
      <span class="text-xs text-muted-color">to</span>
      <DatePicker v-model="customEnd" show-time hour-format="24" size="small" placeholder="To" />
      <Button label="Apply" size="small" :disabled="resolveRange() === null" :loading="loadingHistory"
              @click="refresh" />
    </div>
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <div class="overflow-hidden rounded-xl border border-surface-800 bg-surface-950 shadow-sm">
      <div class="flex items-center justify-between gap-3 border-b border-white/[0.06] px-4 py-2 text-[11px] text-surface-400">
        <span class="flex min-w-0 items-center gap-2">
          <span :class="['h-1.5 w-1.5 shrink-0 rounded-full', following ? 'animate-pulse bg-emerald-400' : 'bg-surface-600']" />
          <span class="truncate">{{ following ? 'Live' : `Logs ${rangeDescription}` }}</span>
        </span>
        <span class="shrink-0 tabular-nums">{{ lineCountText }}</span>
      </div>
      <div v-if="rows.length === 0" class="flex h-[60vh] items-center justify-center font-mono text-xs text-surface-500">
        <span v-if="!loadingHistory">No log entries {{ rangeDescription }}</span>
      </div>
      <VirtualScroller
        v-else
        ref="scroller"
        :items="rows"
        :itemSize="LINE_HEIGHT_PX"
        class="h-[60vh] py-1 font-mono text-[0.78rem] text-surface-200"
        @scroll="onScroll"
      >
        <template #item="{ item }">
          <!-- A continuation row keeps its entry's timestamp and level invisible so its text aligns under the first row's -->
          <div class="flex h-[22px] items-center gap-3 whitespace-pre pr-4 hover:bg-white/[0.04]">
            <span class="shrink-0 select-none border-r border-white/[0.06] px-4 tabular-nums text-surface-500"
                  :class="{ invisible: item.continuation }">{{ formatTimestamp(item.ts) }}</span>
            <span v-if="item.level" class="shrink-0 min-w-[5ch] uppercase" :class="{ invisible: item.continuation }"
                  :style="{ color: LEVEL_COLORS[item.level as LogLevel] }">{{ item.level }}</span>
            <span><span v-for="(segment, i) in item.segments" :key="i" :style="segment.style">{{ segment.text }}</span></span>
          </div>
        </template>
      </VirtualScroller>
    </div>
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
   * its own span rather than the last hour; the run is also offered as a span while it is still going.
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
const LINE_HEIGHT_PX = 22
const DAY_MS = 24 * 60 * 60_000

// The 16 ANSI colors as a dark terminal renders them: normal 0-7, then bright 8-15
const ANSI_PALETTE = [
  '#000000', '#cd3131', '#0dbc79', '#e5e510', '#2472c8', '#bc3fbc', '#11a8cd', '#e5e5e5',
  '#666666', '#f14c4c', '#23d18b', '#f5f543', '#3b8eea', '#d670d6', '#29b8db', '#ffffff'
]
const ANSI_CUBE_STEPS = [0, 95, 135, 175, 215, 255]
// The view's own text and background colors, which inverse video swaps in for a default color
const DEFAULT_FG = 'var(--p-surface-200)'
const DEFAULT_BG = 'var(--p-surface-950)'
// CSI sequences, then OSC, DCS, APC and PM strings, then every other escape: an optional run of
// intermediate bytes, as in a charset designation such as ESC ( B, before one final byte
const ANSI_ESCAPE = /\x1b\[([0-?]*)([ -\/]*)([@-~])|\x1b[\]P_^X][^\x07\x1b]*(?:\x07|\x1b\\)?|\x1b[ -\/]*[0-~]?/g
// A CSI sequence is SGR only when it carries plain numeric parameters; ESC[>4;2m is a keyboard mode, not a style
const SGR_PARAMS = /^[0-9;:]*$/
// The C0 controls a log line can carry besides tab and carriage return, which draw nothing
const INVISIBLE_CONTROLS = /[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]/g
// SGR codes that set or reset one attribute other than a color
const SGR_ATTRIBUTES: Record<number, Partial<AnsiStyle>> = {
  1: { bold: true },
  2: { faint: true },
  3: { italic: true },
  4: { underline: 'solid' },
  7: { inverse: true },
  8: { concealed: true },
  9: { strikethrough: true },
  21: { underline: 'double' },
  22: { bold: false, faint: false },
  23: { italic: false },
  24: { underline: null },
  27: { inverse: false },
  28: { concealed: false },
  29: { strikethrough: false },
  39: { fg: null },
  49: { bg: null },
  53: { overline: true },
  55: { overline: false },
  59: { underlineColor: null }
}
// The styles 4:n selects, by n; 4:0 removes the underline
const UNDERLINE_STYLES: (UnderlineStyle | null)[] = [null, 'solid', 'double', 'wavy', 'dotted', 'dashed']

// The logback levels an OTLP-shipped entry's severity_text names, lowercased, colored as Spring Boot's
// console colors its level column
const LEVEL_COLORS = {
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

type UnderlineStyle = 'solid' | 'double' | 'wavy' | 'dotted' | 'dashed'

/** The SGR attributes in effect at a point in an entry's text. */
interface AnsiStyle {
  fg: string | null
  bg: string | null
  underlineColor: string | null
  underline: UnderlineStyle | null
  bold: boolean
  faint: boolean
  italic: boolean
  inverse: boolean
  concealed: boolean
  strikethrough: boolean
  overline: boolean
}

function hasEnded(run: WorkloadRun | undefined): boolean {
  return run !== undefined && run.finished !== null
}

const spanOptions = computed(() => props.run ? [...PRESET_OPTIONS, RUN_OPTION, CUSTOM_OPTION] : [...PRESET_OPTIONS, CUSTOM_OPTION])

// shallowRef: rows are immutable once parsed, so per-row reactive proxies buy nothing
const rows = shallowRef<LogRow[]>([])
// The entries behind rows; rows outnumber them by every extra line of a multi-line entry
const entryCount = ref(0)
const following = ref(false)
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
// Each refresh supersedes the ones before it, so a slower earlier response neither replaces newer rows nor opens a tail
let refreshGeneration = 0

const rangeDescription = computed(() => {
  let ret: string
  const range = loadedRange.value
  if (span.value === CUSTOM_SPAN && range) {
    ret = `between ${DatetimeUtil.formatDateTime(range.start)} and ${DatetimeUtil.formatDateTime(range.end)}`
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
// severity_text its logger set and the stack trace of the exception it was logged with. The level is that
// severity_text because Loki's detected_level is a keyword guess on a plain-text line, labelling some lines
// of a workload log and leaving the rest bare.
function parseStreams(streams: Array<{ stream?: Record<string, string>; values?: [string, string][] }> | undefined): LogEntry[] {
  const out: LogEntry[] = []
  for (const { stream, values } of streams ?? []) {
    const severity = stream?.severity_text?.toLowerCase()
    const level = severity !== undefined && Object.hasOwn(LEVEL_COLORS, severity) ? severity as LogLevel : null
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
  return {
    fg: null,
    bg: null,
    underlineColor: null,
    underline: null,
    bold: false,
    faint: false,
    italic: false,
    inverse: false,
    concealed: false,
    strikethrough: false,
    overline: false
  }
}

// Splits a line at its ANSI escapes into styled runs, applying SGR escapes to style and dropping the rest
function styleSegments(line: string, style: AnsiStyle): StyledSegment[] {
  const out: StyledSegment[] = []
  // Text after a carriage return redraws the line from its start, as a progress bar does on a terminal
  let redraw = false
  const append = (text: string) => {
    text.replace(INVISIBLE_CONTROLS, '').split('\r').forEach((piece, i) => {
      redraw ||= i > 0
      if (piece.length > 0) {
        if (redraw) {
          out.length = 0
          redraw = false
        }
        out.push({ text: piece, style: toCss(style) })
      }
    })
  }
  let textStart = 0
  for (const match of line.matchAll(ANSI_ESCAPE)) {
    append(line.slice(textStart, match.index))
    if (match[3] === 'm' && match[2] === '' && SGR_PARAMS.test(match[1]!)) {
      applySgr(match[1]!, style)
    }
    textStart = match.index + match[0].length
  }
  append(line.slice(textStart))
  return out
}

function applySgr(params: string, style: AnsiStyle) {
  // A colon-separated field carries its own sub-parameters, as in 38:2::r:g:b or 4:3; an omitted value
  // means 0, so a bare ESC[m is a reset
  const fields = params.split(';').map(field => field.split(':').map(value => value === '' ? 0 : Number(value)))
  for (let i = 0; i < fields.length; i++) {
    const field = fields[i]!
    const code = field[0]!
    if (code === 0) {
      Object.assign(style, plainStyle())
    } else if (code === 4 && field.length > 1) {
      const selected = UNDERLINE_STYLES[field[1]!]
      style.underline = selected === undefined ? 'solid' : selected
    } else if (Object.hasOwn(SGR_ATTRIBUTES, code)) {
      Object.assign(style, SGR_ATTRIBUTES[code])
    } else if (code >= 30 && code <= 37) {
      style.fg = ANSI_PALETTE[code - 30]!
    } else if (code >= 40 && code <= 47) {
      style.bg = ANSI_PALETTE[code - 40]!
    } else if (code >= 90 && code <= 97) {
      style.fg = ANSI_PALETTE[code - 90 + 8]!
    } else if (code >= 100 && code <= 107) {
      style.bg = ANSI_PALETTE[code - 100 + 8]!
    } else if (code === 38 || code === 48 || code === 58) {
      // 5;n picks from the 256-color table and 2;r;g;b is a 24-bit color, either as the fields that follow
      // or as colon sub-parameters, where a 24-bit color may name a color space before its components
      let color: string | null
      if (field.length > 1) {
        const components = field[1] === 2 && field.length >= 6 ? field.slice(3) : field.slice(2)
        color = extendedColor(field[1]!, components)
      } else {
        const selector = fields[i + 1]?.[0]
        const argumentCount = selector === 5 ? 1 : selector === 2 ? 3 : 0
        color = extendedColor(selector, fields.slice(i + 2, i + 2 + argumentCount).map(arg => arg[0]!))
        i += selector === undefined ? 0 : 1 + argumentCount
      }
      if (code === 38) {
        style.fg = color
      } else if (code === 48) {
        style.bg = color
      } else {
        style.underlineColor = color
      }
    }
  }
}

function extendedColor(selector: number | undefined, components: number[]): string | null {
  let ret: string | null
  if (selector === 5) {
    ret = color256(components[0])
  } else if (selector === 2) {
    ret = rgb(components[0], components[1], components[2])
  } else {
    ret = null
  }
  return ret
}

// The xterm 256-color table: the 16 ANSI colors, a 6x6x6 color cube, then a 24-step gray ramp
function color256(index: number | undefined): string | null {
  let ret: string | null
  if (index === undefined || !Number.isInteger(index) || index < 0 || index > 255) {
    ret = null
  } else if (index < 16) {
    ret = ANSI_PALETTE[index]!
  } else if (index < 232) {
    const cube = index - 16
    ret = rgb(ANSI_CUBE_STEPS[Math.floor(cube / 36)], ANSI_CUBE_STEPS[Math.floor(cube / 6) % 6], ANSI_CUBE_STEPS[cube % 6])
  } else {
    const gray = 8 + (index - 232) * 10
    ret = rgb(gray, gray, gray)
  }
  return ret
}

function rgb(...components: (number | undefined)[]): string | null {
  const valid = components.every(component => component !== undefined && Number.isInteger(component) && component >= 0 && component <= 255)
  return valid ? `rgb(${components.join(', ')})` : null
}

function toCss(style: AnsiStyle): string | undefined {
  const declarations: string[] = []
  const fg = style.inverse ? style.bg ?? DEFAULT_BG : style.fg
  const bg = style.inverse ? style.fg ?? DEFAULT_FG : style.bg
  if (fg) {
    declarations.push(`color: ${fg}`)
  }
  if (bg) {
    declarations.push(`background-color: ${bg}`)
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
  const lines = [style.underline && 'underline', style.strikethrough && 'line-through', style.overline && 'overline'].filter(Boolean)
  if (lines.length > 0) {
    declarations.push(`text-decoration-line: ${lines.join(' ')}`)
  }
  if (style.underline && style.underline !== 'solid') {
    declarations.push(`text-decoration-style: ${style.underline}`)
  }
  if (style.underline && style.underlineColor) {
    declarations.push(`text-decoration-color: ${style.underlineColor}`)
  }
  if (style.concealed) {
    declarations.push('visibility: hidden')
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

/** Loads the selected range and, while following, tails on from where the range ends. */
async function refresh() {
  const range = resolveRange()
  if (range === null) {
    return
  }
  const generation = ++refreshGeneration
  // A tail opened before this load would repeat or skip entries around the new range's end
  stopTail()
  loadingHistory.value = true
  error.value = null
  try {
    const bytes = await props.source.history(range.start, range.end, limit.value)
    if (generation === refreshGeneration) {
      // Raw Loki query_range response: {status, data: {result: [{stream, values}]}}
      const body = parseJsonBytes(bytes)
      const entries = parseStreams(body?.data?.result).sort((a, b) => a.ts - b.ts)
      rows.value = toRows(entries)
      entryCount.value = entries.length
      loadedRange.value = range
      // Loki answers newest-first up to the limit, so a full page means the range holds more
      limitReached.value = entries.length >= limit.value
      scrollToBottom()
      if (following.value) {
        // The range's end is exclusive and the tail's start inclusive, so together they read each entry once
        startTail(range.end)
      }
    }
  } catch (err) {
    if (generation === refreshGeneration) {
      following.value = false
      error.value = errorMessage(err, 'Failed to load log history')
    }
  } finally {
    if (generation === refreshGeneration) {
      loadingHistory.value = false
    }
  }
}

function startTail(start: number) {
  tailSubscription = props.source.tail(start).subscribe({
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

onMounted(refresh)

onUnmounted(() => {
  // Supersedes a load still in flight, which would otherwise open a tail nothing closes
  refreshGeneration++
  stopTail()
})

watch(following, follow => {
  if (follow) {
    refresh()
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
    refresh()
  }
})

watch(limit, refresh)

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
