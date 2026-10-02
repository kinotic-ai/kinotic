<template>
  <div class="flex flex-col gap-4">
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <div v-else-if="loading" class="py-6 text-center text-sm text-muted-color">Loading trace…</div>
    <div v-else-if="spans.length === 0" class="py-6 text-center text-sm text-muted-color">The trace holds no spans</div>
    <template v-else>
      <!-- The trace at a glance -->
      <div class="flex flex-wrap items-center gap-x-6 gap-y-2 text-xs">
        <span v-for="fact in summary" :key="fact.label" class="flex items-baseline gap-1.5">
          <span class="text-muted-color">{{ fact.label }}</span>
          <span class="font-medium tabular-nums text-surface-900 dark:text-surface-50">{{ fact.value }}</span>
        </span>
        <span v-if="errorCount > 0"
              class="inline-flex items-center gap-1 rounded-md bg-red-50 px-1.5 py-0.5 font-medium text-red-700 dark:bg-red-500/15 dark:text-red-300">
          <CircleAlert :size="12" :stroke-width="2" aria-hidden="true" />
          {{ errorCount }} failed {{ errorCount === 1 ? 'span' : 'spans' }}
        </span>
        <span class="ml-auto flex items-center gap-1.5">
          <span class="text-muted-color">Trace ID</span>
          <code class="font-mono text-surface-700 dark:text-surface-200">{{ traceId }}</code>
          <button type="button" :class="ICON_BUTTON" :aria-label="copiedKey === 'trace' ? 'Copied' : 'Copy trace ID'"
                  v-tooltip.top="copiedKey === 'trace' ? 'Copied' : 'Copy'" @click="copy('trace', traceId)">
            <component :is="copiedKey === 'trace' ? Check : Copy" :size="13" :stroke-width="1.75" aria-hidden="true" />
          </button>
        </span>
      </div>

      <!-- The waterfall: each span on the trace's timeline, nested under its parent, coloured by its service -->
      <div class="overflow-hidden rounded-lg border border-surface-200 dark:border-surface-700">
        <div class="flex border-b border-surface-200 bg-surface-50 text-[11px] font-medium text-surface-500 dark:border-surface-700 dark:bg-surface-900 dark:text-surface-400">
          <div class="w-[36%] min-w-64 shrink-0 px-4 py-2">Service &amp; operation</div>
          <div class="relative mr-24 flex-1 py-2">
            <span v-for="tick in ticks" :key="tick.percent" class="absolute top-2 -translate-x-1/2 whitespace-nowrap tabular-nums first:translate-x-0 last:-translate-x-full"
                  :style="{ left: `${tick.percent}%` }">{{ tick.label }}</span>
          </div>
        </div>

        <template v-for="{ span, color, bar, labelInside } in rows" :key="span.spanId">
          <button type="button"
                  :class="['group flex w-full items-stretch border-b border-surface-100 text-left text-xs last:border-b-0 dark:border-surface-800',
                           span.spanId === selected?.spanId ? 'bg-surface-100 dark:bg-surface-800' : 'hover:bg-surface-50 dark:hover:bg-surface-800/50']"
                  :aria-expanded="span.spanId === selected?.spanId"
                  @click="toggle(span)">
            <div class="flex w-[36%] min-w-64 shrink-0 items-center gap-2 py-1.5 pr-3"
                 :style="{ paddingLeft: `${16 + span.depth * 16}px` }">
              <span class="h-4 w-[3px] shrink-0 rounded-full" :style="{ background: color }" aria-hidden="true" />
              <CircleAlert v-if="span.error" :size="13" :stroke-width="2" class="shrink-0 text-red-500" aria-label="Failed" />
              <span class="truncate font-medium text-surface-900 dark:text-surface-50" v-tooltip.top="span.name">{{ span.name }}</span>
              <span class="truncate text-muted-color">{{ span.service }}</span>
            </div>
            <div class="relative mr-24 flex-1">
              <span v-for="tick in ticks.slice(1, -1)" :key="tick.percent" class="absolute inset-y-0 w-px bg-surface-100 dark:bg-surface-800"
                    :style="{ left: `${tick.percent}%` }" aria-hidden="true" />
              <span class="absolute top-1/2 h-2.5 -translate-y-1/2 rounded-sm" :style="bar" />
              <span :class="['absolute top-1/2 -translate-y-1/2 whitespace-nowrap font-mono text-[11px] tabular-nums',
                             span.error ? 'font-medium text-red-600 dark:text-red-400' : 'text-surface-500 dark:text-surface-400']"
                    :style="labelInside ? { right: `${100 - parseFloat(bar.left)}%`, marginRight: '6px' } : { left: `calc(${bar.left} + ${bar.width})`, marginLeft: '6px' }">
                {{ formatDuration(span.durationMs) }}
              </span>
            </div>
          </button>

          <!-- The selected span's details open under its row -->
          <div v-if="span.spanId === selected?.spanId"
               class="flex flex-col gap-4 border-b border-l-[3px] border-b-surface-200 bg-surface-0 px-5 py-4 last:border-b-0 dark:border-b-surface-700 dark:bg-surface-950"
               :style="{ borderLeftColor: color }">
            <div class="flex flex-wrap items-center gap-2">
              <span class="text-sm font-semibold text-surface-950 dark:text-surface-0">{{ span.name }}</span>
              <span class="text-xs text-muted-color">{{ span.service }}</span>
              <span class="ml-auto flex flex-wrap items-center gap-1.5">
                <span v-for="chip in spanChips(span)" :key="chip.label" :class="CHIP">
                  <span class="text-surface-500 dark:text-surface-400">{{ chip.label }}</span>
                  <span class="font-mono text-surface-800 dark:text-surface-100">{{ chip.value }}</span>
                </span>
                <span :class="[CHIP, span.error ? '!bg-red-50 !text-red-700 dark:!bg-red-500/15 dark:!text-red-300' : '']">
                  <span :class="['h-1.5 w-1.5 rounded-full', span.error ? 'bg-red-500' : 'bg-green-500']" aria-hidden="true" />
                  {{ span.error ? 'Error' : 'OK' }}
                </span>
              </span>
            </div>

            <p v-if="span.error && span.statusMessage"
               class="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-800 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-200">
              <CircleAlert :size="14" :stroke-width="2" class="mt-px shrink-0" aria-hidden="true" />
              <span class="break-words">{{ span.statusMessage }}</span>
            </p>

            <section v-if="Object.keys(span.attributes).length > 0">
              <h4 :class="SECTION_TITLE">Attributes <span class="font-normal text-muted-color">{{ Object.keys(span.attributes).length }}</span></h4>
              <dl :class="KV_TABLE">
                <div v-for="(value, key) in span.attributes" :key="key" :class="KV_ROW">
                  <dt :class="KV_KEY">{{ key }}</dt>
                  <dd :class="KV_VALUE">{{ value }}</dd>
                </div>
              </dl>
            </section>

            <section v-if="span.events.length > 0">
              <h4 :class="SECTION_TITLE">Events <span class="font-normal text-muted-color">{{ span.events.length }}</span></h4>
              <ol class="flex flex-col gap-3">
                <li v-for="(event, index) in span.events" :key="index"
                    class="rounded-md border border-surface-200 dark:border-surface-700">
                  <div class="flex items-center gap-2 border-b border-surface-100 px-3 py-2 text-xs dark:border-surface-800">
                    <span class="font-mono tabular-nums text-muted-color">+{{ formatDuration(event.timeMs - traceStartMs) }}</span>
                    <span :class="['font-medium', isException(event) ? 'text-red-700 dark:text-red-300' : 'text-surface-900 dark:text-surface-50']">{{ event.name }}</span>
                  </div>
                  <div class="flex flex-col gap-2 px-3 py-2.5">
                    <p v-if="isException(event) && (event.attributes['exception.type'] || event.attributes['exception.message'])" class="text-xs">
                      <span class="font-mono font-medium text-surface-950 dark:text-surface-0">{{ event.attributes['exception.type'] }}</span>
                      <template v-if="event.attributes['exception.message']">
                        <span class="text-muted-color">: </span>
                        <span class="text-surface-800 dark:text-surface-100">{{ event.attributes['exception.message'] }}</span>
                      </template>
                    </p>
                    <dl v-if="otherEventAttributes(event).length > 0" :class="KV_TABLE">
                      <div v-for="[key, value] in otherEventAttributes(event)" :key="key" :class="KV_ROW">
                        <dt :class="KV_KEY">{{ key }}</dt>
                        <dd :class="KV_VALUE">{{ value }}</dd>
                      </div>
                    </dl>
                    <div v-if="event.attributes['exception.stacktrace']" class="overflow-hidden rounded-md border border-surface-800 bg-surface-950">
                      <div class="flex items-center justify-between border-b border-white/[0.06] px-3 py-1.5 text-[11px] text-surface-400">
                        <span>Stack trace</span>
                        <button type="button" class="flex h-6 w-6 items-center justify-center rounded text-surface-400 transition-colors hover:bg-white/10 hover:text-surface-0"
                                :aria-label="copiedKey === `stack-${index}` ? 'Copied' : 'Copy stack trace'"
                                v-tooltip.top="copiedKey === `stack-${index}` ? 'Copied' : 'Copy'"
                                @click="copy(`stack-${index}`, event.attributes['exception.stacktrace'])">
                          <component :is="copiedKey === `stack-${index}` ? Check : Copy" :size="13" :stroke-width="1.75" aria-hidden="true" />
                        </button>
                      </div>
                      <pre class="m-0 max-h-72 overflow-auto px-3 py-2 font-mono text-[11.5px] leading-5 text-surface-200">{{ formatStack(event.attributes['exception.stacktrace']) }}</pre>
                    </div>
                  </div>
                </li>
              </ol>
            </section>

            <!-- The resource is the emitting process, the same for every span of a service, so it
                 opens on request; a value that runs to paragraphs (the JVM's command line) clamps
                 until clicked -->
            <section v-if="Object.keys(span.resource).length > 0">
              <button type="button" :class="[SECTION_TITLE, 'flex items-center gap-1.5']" :aria-expanded="resourceOpen" @click="resourceOpen = !resourceOpen">
                <ChevronRight :size="14" :stroke-width="2" :class="['transition-transform', resourceOpen ? 'rotate-90' : '']" aria-hidden="true" />
                Resource <span class="font-normal text-muted-color">{{ Object.keys(span.resource).length }}</span>
              </button>
              <dl v-if="resourceOpen" :class="KV_TABLE">
                <div v-for="(value, key) in span.resource" :key="key" :class="KV_ROW">
                  <dt :class="KV_KEY">{{ key }}</dt>
                  <dd :class="[KV_VALUE, expandedKeys.has(key) ? '' : 'line-clamp-2 cursor-pointer']"
                      v-tooltip.top="expandedKeys.has(key) ? undefined : 'Click to show the whole value'"
                      @click="expandedKeys.add(key)">{{ value }}</dd>
                </div>
              </dl>
            </section>
          </div>
        </template>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Message from 'primevue/message'
import { Check, ChevronRight, CircleAlert, Copy } from '@lucide/vue'

import { isDark } from '../../composables/useTheme'
import DatetimeUtil from '../../util/DatetimeUtil'
import { errorMessage } from '../../util/helpers'
import type { TraceSpan } from './TraceSpan'
import type { TraceSpanEvent } from './TraceSpanEvent'
import { fetchTrace } from './telemetryApi'
import { accentColor } from '../../charts/chartTheme'
import { formatDuration, serviceColor } from './telemetryDisplay'

/**
 * One trace as a waterfall of its spans on the trace's timeline, nested under their parents and
 * coloured by the service that emitted them. Clicking a span opens its status, attributes, events
 * and resource under its row; the trace opens on its first failed span, or its root.
 */
const props = defineProps<{
  organizationId: string | null
  traceId: string
}>()

const CHIP = 'inline-flex items-center gap-1.5 rounded-md bg-surface-100 px-2 py-0.5 text-[11px] font-medium text-surface-700 dark:bg-surface-800 dark:text-surface-200'
const ICON_BUTTON = 'flex h-6 w-6 items-center justify-center rounded text-surface-500 transition-colors hover:bg-surface-100 hover:text-surface-950 dark:hover:bg-surface-800 dark:hover:text-surface-0'
const SECTION_TITLE = 'mb-2 text-xs font-semibold text-surface-900 dark:text-surface-50'
const KV_TABLE = 'overflow-hidden rounded-md border border-surface-200 text-xs dark:border-surface-700'
const KV_ROW = 'grid grid-cols-[minmax(10rem,16rem)_1fr] border-b border-surface-100 last:border-b-0 dark:border-surface-800'
const KV_KEY = 'bg-surface-50 px-3 py-1.5 text-surface-500 dark:bg-surface-900 dark:text-surface-400 [overflow-wrap:anywhere]'
const KV_VALUE = 'm-0 px-3 py-1.5 font-mono text-surface-900 dark:text-surface-100 [overflow-wrap:anywhere]'

// Event attributes the exception block shows in its own form
const EXCEPTION_KEYS = new Set(['exception.type', 'exception.message', 'exception.stacktrace'])

const spans = ref<TraceSpan[]>([])
const selected = ref<TraceSpan | null>(null)
const resourceOpen = ref(false)
/** The resource keys whose whole value the user asked to see; a new selection folds them again. */
const expandedKeys = ref(new Set<string>())
const copiedKey = ref<string | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const traceStartMs = computed(() => Math.min(...spans.value.map(span => span.startMs)))
const traceDurationMs = computed(() => Math.max(1, ...spans.value.map(span => span.startMs + span.durationMs - traceStartMs.value)))
const errorCount = computed(() => spans.value.filter(span => span.error).length)

const summary = computed(() => [
  { label: 'Started', value: DatetimeUtil.formatDateFromEpoch(traceStartMs.value) },
  { label: 'Duration', value: formatDuration(traceDurationMs.value) },
  { label: 'Spans', value: String(spans.value.length) },
  { label: 'Services', value: String(new Set(spans.value.map(span => span.service)).size) }
])

/** The ruler's marks: the start, quarters of the trace, and its end. */
const ticks = computed(() => [0, 25, 50, 75, 100].map(percent => ({
  percent,
  label: percent === 0 ? '0' : formatDuration((traceDurationMs.value * percent) / 100)
})))

// Each row's geometry and colour, derived once per trace rather than per render: selecting a
// span re-renders every row. A bar spans its share of the trace, never thinner than a sliver;
// its duration sits after it, or before it when the bar runs to the end.
const rows = computed(() => spans.value.map(span => {
  const left = Math.min(((span.startMs - traceStartMs.value) / traceDurationMs.value) * 100, 99.7)
  const width = Math.min(Math.max(0.3, (span.durationMs / traceDurationMs.value) * 100), 100 - left)
  const color = serviceColor(span.service, isDark.value)
  return {
    span,
    color,
    labelInside: left + width > 80 && left > 20,
    // A failed span's bar is solid red; the rest keep their service's colour
    bar: { left: `${left}%`, width: `${width}%`, background: span.error ? accentColor('red', isDark.value) : color }
  }
}))

function spanChips(span: TraceSpan): { label: string, value: string }[] {
  return [
    { label: 'Kind', value: span.kind },
    { label: 'Duration', value: formatDuration(span.durationMs) },
    { label: 'Start', value: `+${formatDuration(span.startMs - traceStartMs.value)}` },
    { label: 'Span ID', value: span.spanId }
  ]
}

function isException(event: TraceSpanEvent): boolean {
  return event.name === 'exception'
}

function otherEventAttributes(event: TraceSpanEvent): [string, string][] {
  return Object.entries(event.attributes).filter(([key]) => !isException(event) || !EXCEPTION_KEYS.has(key))
}

// A JVM stack trace that arrives on one line breaks before each frame, as the JVM prints it
function formatStack(stack: string): string {
  return stack.includes('\n') ? stack : stack.replace(/\s+(at |Caused by: |\.\.\. \d+ more)/g, '\n\t$1')
}

function toggle(span: TraceSpan) {
  selected.value = selected.value?.spanId === span.spanId ? null : span
}

async function copy(key: string, text: string): Promise<void> {
  await navigator.clipboard.writeText(text)
  copiedKey.value = key
  setTimeout(() => {
    if (copiedKey.value === key) {
      copiedKey.value = null
    }
  }, 1500)
}

async function load() {
  loading.value = true
  error.value = null
  selected.value = null
  try {
    spans.value = await fetchTrace(props.organizationId, props.traceId)
    selected.value = spans.value.find(span => span.error) ?? spans.value[0] ?? null
  } catch (err) {
    spans.value = []
    error.value = errorMessage(err, 'Failed to load the trace')
  } finally {
    loading.value = false
  }
}

watch(() => [props.organizationId, props.traceId], load, { immediate: true })

watch(selected, () => expandedKeys.value.clear())
</script>
