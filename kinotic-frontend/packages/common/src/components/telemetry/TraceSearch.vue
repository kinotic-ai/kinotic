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
                  @click="searchStatus(option.onlyErrors)">
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
      :selection="highlightedTrace"
      v-model:sort-field="sortField"
      v-model:sort-order="sortOrder"
      :value="traces"
      dataKey="traceId"
      selectionMode="single"
      :meta-key-selection="false"
      :table-style="{ tableLayout: 'fixed' }"
      :class="['text-sm', { 'datatable-loading': loading }]"
      @row-click="openTrace($event.data)"
      @mouseover="onRowsMouseover"
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
          <TableChip :icon="ChartGantt">{{ data.matchedSpans }} {{ data.matchedSpans === 1 ? 'span' : 'spans' }}</TableChip>
        </template>
      </Column>
      <Column header="Trace" style="width: 7.75rem">
        <template #body="{ data }">
          <span class="rounded-md bg-surface-100 px-1.5 py-0.5 font-mono text-[0.6875rem] text-surface-600 dark:bg-surface-800 dark:text-surface-300">{{ data.traceId.slice(0, 12) }}</span>
        </template>
      </Column>
    </DataTable>
    </div>

    <!-- The picked trace opens beside the list; the arrows step through the list in its current order -->
    <SteppingDrawer v-model:visible="drawerVisible" :position="position" :total="orderedTraces.length"
                    :expand-to="traceRoute && selectedTrace ? traceRoute(selectedTrace.traceId) : undefined"
                    expand-label="Open the trace's page" @step="stepTrace">
      <template #title>
        <template v-if="selectedTrace">
          <span class="h-2 w-2 shrink-0 rounded-full" :style="{ background: serviceColor(selectedTrace.rootService) }" aria-hidden="true" />
          <span class="truncate text-sm font-medium text-surface-950 dark:text-surface-0" v-tooltip.bottom="selectedTrace.rootName">{{ selectedTrace.rootName }}</span>
          <span class="hidden truncate text-xs text-muted-color sm:inline">{{ selectedTrace.rootService }}</span>
        </template>
      </template>
      <TraceDetail v-if="selectedTrace" :organization-id="organizationId" :trace-id="selectedTrace.traceId" />
    </SteppingDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import { ChartGantt } from '@lucide/vue'
import EmptyChartCharacter from '../EmptyChartCharacter.vue'
import SteppingDrawer from '../SteppingDrawer.vue'
import { useSteppingDrawer } from '../../composables/useSteppingDrawer'
import TableChip from '../TableChip.vue'

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
 * the one picked from the results in a drawer beside them, whose arrows and the arrow keys step
 * through the results in their current order; {@code traceRoute}, when given, links from there to
 * the trace's own page. searchStatus(onlyErrors) narrows the search to the traces with a failed
 * span, or widens it back to every trace, and runs it.
 */
const props = defineProps<{
  organizationId: string | null
  applicationId: string | null
  range: TimeRange
  traceRoute?: (traceId: string) => RouteLocationRaw
}>()

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
const sortField = ref<string>('startMs')
const sortOrder = ref<number>(-1)

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

/** The traces in the order the table shows them, which the drawer's arrows step through. */
const orderedTraces = computed(() => {
  const field = sortField.value as keyof TraceSummary
  return [...traces.value].sort((a, b) => {
    const left = a[field]
    const right = b[field]
    const compared = typeof left === 'number' && typeof right === 'number'
        ? left - right
        : String(left ?? '').localeCompare(String(right ?? ''))
    return compared * sortOrder.value
  })
})

const { selected: selectedTrace, visible: drawerVisible, position, open: openTrace, step: stepTrace,
        highlighted: highlightedTrace, hover: hoverTrace } =
    useSteppingDrawer(orderedTraces, trace => trace.traceId)

// The pointer moving onto a trace lets a kept selection go; the table shows orderedTraces' order
function onRowsMouseover(event: MouseEvent) {
  const row = (event.target as Element | null)?.closest?.('tbody > tr')
  const index = row?.parentElement ? Array.from(row.parentElement.children).indexOf(row) : -1
  const trace = index >= 0 ? orderedTraces.value[index] : undefined
  if (trace) {
    hoverTrace(trace)
  }
}

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

function searchStatus(onlyErrors: boolean) {
  filters.onlyErrors = onlyErrors
  return search()
}

// The panel replaces the range on every refresh and scope change, so it is the one trigger
watch(() => props.range, search, { immediate: true })

defineExpose({ searchStatus })
</script>

<style>
/* The card's border closes the list, so the last row draws no line of its own */
.trace-list .p-datatable-tbody > tr:last-child > td {
  border-bottom-width: 0;
}
</style>
