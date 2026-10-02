<template>
  <div class="flex flex-col gap-3">
    <div class="trace-filters flex flex-wrap items-end gap-3">
      <div class="flex flex-col gap-1">
        <label class="text-xs text-muted-color">Service</label>
        <InputText v-model="filters.service" size="small" placeholder="Any service" @keyup.enter="search" />
      </div>
      <div class="flex flex-col gap-1">
        <label class="text-xs text-muted-color">Span name</label>
        <InputText v-model="filters.spanName" size="small" placeholder="Any span" @keyup.enter="search" />
      </div>
      <div class="flex flex-col gap-1">
        <label class="text-xs text-muted-color">Min duration (ms)</label>
        <InputNumber v-model="filters.minDurationMs" size="small" :min="0" :useGrouping="false" placeholder="0" inputClass="w-28" />
      </div>
      <div class="flex flex-col gap-1">
        <span id="trace-status-label" class="text-xs text-muted-color">Status</span>
        <!-- both choices stay in view, so which one applies is never in doubt -->
        <div role="radiogroup" aria-labelledby="trace-status-label"
             class="inline-flex h-9 items-center gap-1 rounded-lg border border-surface-200 bg-surface-50 p-1 dark:border-surface-700 dark:bg-surface-800/60">
          <button v-for="option in STATUS_OPTIONS" :key="option.label" type="button" role="radio"
                  :aria-checked="filters.onlyErrors === option.onlyErrors"
                  :class="['flex h-full items-center gap-2 rounded-md px-3 text-[0.8125rem] transition-colors',
                           filters.onlyErrors === option.onlyErrors
                             ? 'bg-surface-0 font-medium text-surface-950 shadow-sm ring-1 ring-surface-200 dark:bg-surface-900 dark:text-surface-0 dark:ring-surface-700'
                             : 'text-surface-600 hover:bg-surface-100 hover:text-surface-950 dark:text-surface-300 dark:hover:bg-surface-800 dark:hover:text-surface-0']"
                  @click="filters.onlyErrors = option.onlyErrors">
            <span v-if="option.onlyErrors" class="h-2 w-2 rounded-full bg-red-500" aria-hidden="true" />
            {{ option.label }}
          </button>
        </div>
      </div>
      <Button label="Search" icon="pi pi-search" size="small" :loading="loading" @click="search" />
    </div>

    <!-- The query the filters amount to, as one would paste it into Tempo -->
    <div class="flex items-center gap-3 rounded-lg border border-surface-200 bg-surface-50 py-1.5 pl-1.5 pr-1.5 dark:border-surface-700 dark:bg-surface-800/60">
      <span class="shrink-0 rounded-md bg-surface-0 px-2 py-1 text-[0.6875rem] font-semibold uppercase tracking-wider text-surface-600 ring-1 ring-surface-200 dark:bg-surface-900 dark:text-surface-300 dark:ring-surface-700">TraceQL</span>
      <code class="min-w-0 flex-1 truncate font-mono text-[0.8125rem]" v-tooltip.top="query">
        <span v-for="(token, position) in queryTokens" :key="position" :class="TOKEN_CLASSES[token.kind]">{{ token.text }}</span>
      </code>
      <Button :icon="copied ? 'pi pi-check' : 'pi pi-copy'" severity="secondary" text size="small"
              :aria-label="copied ? 'Copied' : 'Copy query'" v-tooltip.top="copied ? 'Copied' : 'Copy query'" @click="copyQuery" />
    </div>

    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <!-- Tempo returns the first traces its search finds, up to the limit, in no order of its
         own; the columns sort within that set, and the caption says so -->
    <p v-if="traces.length > 0" class="text-xs text-muted-color">
      {{ traces.length }} traces Tempo found in the range{{ traces.length >= SEARCH_LIMIT ? ', its limit' : '' }}; the columns sort within them.
    </p>
    <div class="trace-list overflow-hidden rounded-xl border border-surface-200 dark:border-surface-700">
    <DataTable
      :value="traces"
      dataKey="traceId"
      selectionMode="single"
      sort-field="startMs"
      :sort-order="-1"
      :table-style="{ tableLayout: 'fixed' }"
      :class="['text-sm', { 'datatable-loading': loading }]"
      @row-select="openTrace($event.data)"
    >
      <template #empty>
        <div v-if="loading" class="py-6 text-center text-sm text-muted-color">Searching traces…</div>
        <EmptyChartCharacter v-else class="py-8" title="No traces match in this range"
                             hint="Widen the time range or loosen the filters." />
      </template>
      <Column field="rootName" header="Operation" sortable>
        <template #body="{ data }">
          <span class="block truncate font-medium text-surface-950 dark:text-surface-0" v-tooltip.top="data.rootName">{{ data.rootName }}</span>
          <span class="mt-0.5 flex items-center gap-1.5 text-xs text-muted-color">
            <span class="h-2 w-2 shrink-0 rounded-full" :style="{ background: serviceColor(data.rootService) }" aria-hidden="true" />
            <span class="truncate">{{ data.rootService }}</span>
          </span>
        </template>
      </Column>
      <Column field="startMs" header="Started" sortable style="width: 15%">
        <template #body="{ data }">
          <span class="block font-mono text-xs tabular-nums text-surface-800 dark:text-surface-100"
                v-tooltip.top="formatDateFromEpoch(data.startMs)">{{ formatClock(data.startMs) }}</span>
          <span v-if="!isToday(data.startMs)" class="block text-xs text-muted-color">{{ formatDay(data.startMs) }}</span>
        </template>
      </Column>
      <Column field="durationMs" header="Duration" sortable style="width: 20%">
        <template #body="{ data }">
          <span class="flex items-center gap-2.5">
            <span class="h-1.5 w-24 shrink-0 overflow-hidden rounded-full bg-surface-100 dark:bg-surface-800" aria-hidden="true">
              <span class="block h-full rounded-full bg-sky-500" :style="{ width: `${durationShare(data.durationMs)}%` }" />
            </span>
            <span class="whitespace-nowrap font-mono text-xs tabular-nums text-surface-800 dark:text-surface-100">{{ formatDuration(data.durationMs) }}</span>
          </span>
        </template>
      </Column>
      <Column field="matchedSpans" header="Spans" sortable style="width: 14%">
        <template #body="{ data }">
          <span class="inline-flex items-center gap-1.5 whitespace-nowrap rounded-md bg-surface-100 px-2 py-0.5 font-sans text-xs font-medium text-surface-700 dark:bg-surface-800 dark:text-surface-200">
            <ChartGantt :size="13" :stroke-width="1.75" aria-hidden="true" />
            <span class="tabular-nums">{{ data.matchedSpans }} {{ data.matchedSpans === 1 ? 'span' : 'spans' }}</span>
          </span>
        </template>
      </Column>
      <Column header="Trace" style="width: 7.75rem">
        <template #body="{ data }">
          <span class="rounded-md bg-surface-100 px-1.5 py-0.5 font-mono text-[0.6875rem] text-surface-600 dark:bg-surface-800 dark:text-surface-300">{{ data.traceId.slice(0, 12) }}</span>
        </template>
      </Column>
    </DataTable>
    </div>

    <Dialog v-if="!traceRoute" v-model:visible="detailVisible" modal maximizable :style="{ width: '90vw' }" :header="detailHeader">
      <TraceDetail v-if="selectedTraceId" :organization-id="organizationId" :trace-id="selectedTraceId" />
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter, type RouteLocationRaw } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import { ChartGantt } from '@lucide/vue'
import EmptyChartCharacter from '../EmptyChartCharacter.vue'

import '../../styles/datatable-loading.css'
import DatetimeUtil from '../../util/DatetimeUtil'
import { seriesColor } from '../../charts/chartTheme'
import { isDark } from '../../composables/useTheme'
import { errorMessage } from '../../util/helpers'
import TraceDetail from './TraceDetail.vue'
import type { TimeRange } from './TimeRange'
import type { TraceSummary } from './TraceSummary'
import { searchTraces, traceQl, type TraceFilters } from './telemetryApi'
import { formatDuration } from './telemetryDisplay'

/**
 * Searches the organization's traces — or one application's — over the given range, and opens
 * the one picked from the results: on the page {@code traceRoute} names when given, otherwise
 * in a dialog over the results. searchErrors() narrows the search to the traces with a failed
 * span and runs it.
 */
const props = defineProps<{
  organizationId: string | null
  applicationId: string | null
  range: TimeRange
  traceRoute?: (traceId: string) => RouteLocationRaw
}>()

const router = useRouter()

/** How many traces one search returns. */
const SEARCH_LIMIT = 50

// What the user narrows by; the application comes from the props at query time
const filters = reactive<Omit<TraceFilters, 'applicationId'>>({
  service: '',
  spanName: '',
  onlyErrors: false,
  minDurationMs: null
})

const traces = ref<TraceSummary[]>([])
const loading = ref(false)
// Searches can overlap when the range or the filters change mid-flight; only the latest lands
let searchSequence = 0
const error = ref<string | null>(null)
const selectedTraceId = ref<string | null>(null)
const detailVisible = ref(false)

/** The status filter's choices: every trace, or only the ones that hold an error. */
const STATUS_OPTIONS = [
  { label: 'All', onlyErrors: false },
  { label: 'Errors', onlyErrors: true }
]

const query = computed(() => traceQl({ ...filters, applicationId: props.applicationId }))

// Each service keeps one colour down the list, in the order the services first appear
const serviceOrder = computed(() => [...new Set(traces.value.map(trace => trace.rootService))])

function serviceColor(service: string): string {
  return seriesColor(Math.max(0, serviceOrder.value.indexOf(service)), isDark.value)
}

const slowest = computed(() => Math.max(1, ...traces.value.map(trace => trace.durationMs)))

/** A trace's duration as a share of the slowest in the list, never too thin to see. */
function durationShare(durationMs: number): number {
  return Math.max(3, Math.round((durationMs / slowest.value) * 100))
}

function formatClock(epochMillis: number): string {
  return new Date(epochMillis).toLocaleTimeString('en-US', { hour12: false })
}

function formatDay(epochMillis: number): string {
  return new Date(epochMillis).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

function isToday(epochMillis: number): boolean {
  return new Date(epochMillis).toDateString() === new Date().toDateString()
}

type TokenKind = 'string' | 'number' | 'attribute' | 'punctuation' | 'space'

// The query's parts coloured by role, so the attributes and the values they match stand out
const TOKEN_CLASSES: Record<TokenKind, string> = {
  string: 'text-emerald-700 dark:text-emerald-300',
  number: 'text-amber-700 dark:text-amber-300',
  attribute: 'text-sky-700 dark:text-sky-300',
  punctuation: 'text-surface-400 dark:text-surface-500',
  space: ''
}

const TOKEN_PATTERN = /("(?:[^"\\]|\\.)*")|(\d+(?:ms|s)?\b)|([A-Za-z_][\w.]*)|(\s+)|(.)/g

const queryTokens = computed<{ text: string, kind: TokenKind }[]>(() =>
    [...query.value.matchAll(TOKEN_PATTERN)].map(match => {
      let kind: TokenKind
      if (match[1]) {
        kind = 'string'
      } else if (match[2]) {
        kind = 'number'
      } else if (match[3]) {
        kind = 'attribute'
      } else if (match[4]) {
        kind = 'space'
      } else {
        kind = 'punctuation'
      }
      return { text: match[0], kind }
    }))

const copied = ref(false)

async function copyQuery(): Promise<void> {
  await navigator.clipboard.writeText(query.value)
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}
const formatDateFromEpoch = DatetimeUtil.formatDateFromEpoch

const detailHeader = computed(() => {
  const selected = traces.value.find(trace => trace.traceId === selectedTraceId.value)
  return selected ? `${selected.rootService} — ${selected.rootName}` : 'Trace'
})

async function search() {
  const sequence = ++searchSequence
  loading.value = true
  error.value = null
  try {
    const found = await searchTraces(props.organizationId, query.value, props.range, SEARCH_LIMIT)
    if (sequence === searchSequence) {
      traces.value = found
    }
  } catch (err) {
    if (sequence === searchSequence) {
      traces.value = []
      error.value = errorMessage(err, 'Failed to search traces')
    }
  } finally {
    if (sequence === searchSequence) {
      loading.value = false
    }
  }
}

function searchErrors() {
  filters.onlyErrors = true
  return search()
}

function openTrace(trace: TraceSummary) {
  if (props.traceRoute) {
    router.push(props.traceRoute(trace.traceId))
  } else {
    selectedTraceId.value = trace.traceId
    detailVisible.value = true
  }
}

// The panel replaces the range on every refresh and scope change, so it is the one trigger
watch(() => props.range, search, { immediate: true })

defineExpose({ searchErrors })
</script>

<style>
/* The card's border closes the list, so the last row draws no line of its own */
.trace-list .p-datatable-tbody > tr:last-child > td {
  border-bottom-width: 0;
}
</style>
