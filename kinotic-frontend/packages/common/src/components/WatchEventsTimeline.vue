<template>
  <div v-if="entries.length === 0 && loading" class="listing-loading h-24" />
  <EmptyChartCharacter v-else-if="entries.length === 0" class="py-6" :title="emptyText" />
  <div v-else :class="['px-5 py-4', { 'listing-loading': loading }]">
    <section v-for="day in days" :key="day.key" class="mb-2 last:mb-0">
      <h3 class="mb-1 text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-color">{{ day.label }}</h3>
      <ol>
        <li v-for="(row, position) in day.rows" :key="row.key" class="relative flex gap-3 pb-3.5 last:pb-2">
          <!-- the rail joins each marker to the next; the day's last entry draws none -->
          <span v-if="position < day.rows.length - 1" class="absolute left-[11px] top-7 bottom-1 w-px bg-surface-200 dark:bg-surface-700" aria-hidden="true" />
          <span :class="['relative z-[1] mt-0.5 flex h-[22px] w-[22px] shrink-0 items-center justify-center rounded-md', TONES[row.tone].marker]">
            <component :is="ICONS[row.entry.eventType] ?? Circle" :size="13" :stroke-width="1.75" aria-hidden="true" />
          </span>
          <div class="min-w-0 flex-1 pt-0.5">
            <p class="flex flex-wrap items-baseline gap-x-1.5 text-[0.8125rem] text-surface-900 dark:text-surface-50">
              <template v-if="showRecord">
                <RouterLink v-if="row.link" :to="row.link" class="font-semibold hover:underline" v-tooltip.top="row.entry.id">{{ row.recordLabel }}</RouterLink>
                <span v-else class="font-semibold" v-tooltip.top="row.entry.id">{{ row.recordLabel }}</span>
              </template>
              <span>
                {{ row.headline.lead }}
                <span v-if="row.headline.word" :class="['rounded px-1 font-medium', TONES[row.tone].text]">{{ row.headline.word }}</span>
                <template v-if="row.commit">
                  at <span class="font-mono text-[0.8125rem]">{{ shortSha(row.commit) }}</span>
                </template>
                {{ row.headline.trail }}
              </span>
            </p>
            <p v-if="row.reason" :class="['mt-0.5 break-words text-xs', row.tone === 'danger' ? 'text-red-700 dark:text-red-300' : 'text-surface-600 dark:text-surface-300']">
              {{ row.reason }}
            </p>
            <p class="mt-0.5 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color">
              <span v-tooltip.top="formatDateTime(row.entry['@timestamp'])" class="tabular-nums">{{ formatTime(row.entry['@timestamp']) }}</span>
              <template v-if="showRecord">
                <span aria-hidden="true">·</span>
                <span>{{ RECORD_LABELS[row.entry.type] ?? row.entry.type }}</span>
              </template>
              <template v-if="showScope">
                <span aria-hidden="true">·</span>
                <span class="inline-flex items-center gap-1">
                  <Building2 :size="12" :stroke-width="1.75" aria-hidden="true" />
                  <span class="max-w-[12rem] truncate" v-tooltip.top="row.entry.scope ?? undefined">{{ row.entry.scope ?? 'system' }}</span>
                </span>
              </template>
              <span aria-hidden="true">·</span>
              <span class="max-w-[22rem] truncate" v-tooltip.top="row.entry.source">by <span class="font-mono">{{ row.entry.source }}</span></span>
              <span aria-hidden="true">·</span>
              <button type="button" class="inline-flex items-center gap-0.5 hover:text-surface-950 dark:hover:text-surface-0"
                      :aria-expanded="opened.has(row.key)" @click="toggle(row.key)">
                Details
                <ChevronDown :size="12" :stroke-width="1.75" :class="['transition-transform', opened.has(row.key) ? 'rotate-180' : '']" aria-hidden="true" />
              </button>
            </p>
            <div v-if="opened.has(row.key)"
                 class="mt-2 rounded-md border border-surface-200 bg-surface-50 px-3 py-2 text-xs dark:border-surface-700 dark:bg-surface-900">
              <dl class="grid grid-cols-[8rem_minmax(0,1fr)] gap-x-3 gap-y-1">
                <template v-for="fact in detailsOf(row.entry)" :key="fact.label">
                  <dt class="text-muted-color">{{ fact.label }}</dt>
                  <dd class="m-0 break-all font-mono text-surface-800 dark:text-surface-100">{{ fact.value }}</dd>
                </template>
              </dl>
              <pre v-if="row.entry.value !== null && row.entry.value !== undefined"
                   class="mt-2 max-h-64 overflow-auto rounded bg-surface-0 p-2 font-mono text-[0.6875rem] leading-relaxed text-surface-800 dark:bg-surface-950 dark:text-surface-100">{{ JSON.stringify(row.entry.value, null, 2) }}</pre>
            </div>
          </div>
        </li>
      </ol>
    </section>

    <button v-if="hiddenCount > 0" type="button"
            class="ml-[34px] mt-1 inline-flex items-center gap-1.5 text-sm font-medium text-surface-600 transition-colors hover:text-surface-950 dark:text-surface-300 dark:hover:text-surface-0"
            @click="expanded = true">
      <ChevronsDown :size="15" :stroke-width="1.75" aria-hidden="true" />
      Show {{ hiddenCount }} earlier {{ hiddenCount === 1 ? 'entry' : 'entries' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, reactive, ref, type Component } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import { Activity, Building2, Check, ChevronDown, ChevronsDown, Circle, Eye, RefreshCw, Target, Trash2, TriangleAlert } from '@lucide/vue'
import { WatchEventType, WatchedType, type WatchEvent } from '@kinotic-ai/management-api'
import DatetimeUtil from '../util/DatetimeUtil'
import { shortSha, withoutExceptionPrefix } from '../util/helpers'
import { TINTS } from '../util/tints'
import EmptyChartCharacter from './EmptyChartCharacter.vue'
import '../styles/datatable-loading.css'

/**
 * A record's ledger entries, newest first, as a timeline grouped by day: when each write landed,
 * what it did, why, and what caused it, each marked in the colour of its outcome. A failure shows
 * its reason under the entry, and each entry opens onto everything the ledger holds for it. A
 * listing that mixes records, a project's deployment history, names the record on each entry.
 */
const props = withDefaults(defineProps<{
  entries: WatchEvent[]
  /** Whether each entry names the record it belongs to, for a listing across records. */
  showRecord?: boolean
  /** Whether each entry names the organization its record belongs to, for a listing across organizations. */
  showScope?: boolean
  /** Whether the caller pages the entries, so every entry given shows at once. */
  paged?: boolean
  /** Where an entry's record opens, null for a record with no page; the record shows unlinked without one. */
  recordLink?: (entry: WatchEvent) => RouteLocationRaw | null
  /** Whether the entries are being loaded, which runs the loading line along the listing's top edge. */
  loading?: boolean
  emptyText?: string
}>(), {
  showRecord: false,
  showScope: false,
  paged: false,
  recordLink: undefined,
  loading: false,
  emptyText: 'Nothing has happened to it yet.'
})

type Tone = 'danger' | 'warn' | 'success' | 'info' | 'neutral'

/** What an entry says happened: words, then the state they name, highlighted, then more words. */
interface Headline {
  lead: string
  word: string | null
  trail: string
}

interface Row {
  key: string
  entry: WatchEvent
  recordLabel: string
  link: RouteLocationRaw | null
  headline: Headline
  commit: string | null
  reason: string | null
  tone: Tone
}

/** How many entries show before the rest are asked for. */
const INITIAL_ENTRIES = 12

// Markers take the same light-tint, strong-icon treatment as every TINTS icon tile
const TONES: Record<Tone, { marker: string, text: string }> = {
  danger: { marker: TINTS.red, text: 'bg-red-50 text-red-700 dark:bg-red-500/15 dark:text-red-300' },
  warn: { marker: 'bg-amber-100 text-amber-600 dark:bg-amber-500/15 dark:text-amber-300', text: 'bg-amber-50 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300' },
  success: { marker: TINTS.green, text: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300' },
  info: { marker: TINTS.primary, text: 'bg-primary-50 text-primary-700 dark:bg-primary-500/15 dark:text-primary-300' },
  neutral: {
    marker: 'bg-surface-100 text-surface-500 dark:bg-surface-800 dark:text-surface-400',
    text: 'bg-surface-100 text-surface-700 dark:bg-surface-800 dark:text-surface-200'
  }
}

const ICONS: Record<string, Component> = {
  [WatchEventType.CONDITION_SET]: markRaw(TriangleAlert),
  [WatchEventType.CONDITION_CLEARED]: markRaw(Check),
  [WatchEventType.STATUS_CHANGED]: markRaw(Activity),
  [WatchEventType.DESIRED_UPDATED]: markRaw(Target),
  [WatchEventType.DESIRED_RENEWED]: markRaw(RefreshCw),
  [WatchEventType.OBSERVED_REPORTED]: markRaw(Eye),
  [WatchEventType.DELETION_REQUESTED]: markRaw(Trash2)
}

const RECORD_LABELS: Record<string, string> = {
  [WatchedType.WORKLOAD]: 'Workload',
  [WatchedType.PROJECT_DEPLOYMENT]: 'Project deployment',
  [WatchedType.MICROSERVICE_DEPLOYMENT]: 'Microservice deployment',
  [WatchedType.UI_DEPLOYMENT]: 'UI deployment',
  [WatchedType.VM_NODE]: 'Node',
  [WatchedType.JOB_RUN]: 'Job run'
}

// The words of a phase or run status, by the outcome they mean
const FAILED_WORDS = new Set(['FAILED'])
const DONE_WORDS = new Set(['DEPLOYED', 'READY', 'COMPLETED', 'ONLINE'])
const ACTIVE_WORDS = new Set(['DEPLOYING', 'PROVISIONING', 'RUNNING', 'PENDING', 'PUBLISHING', 'STARTING', 'STOPPING'])

const formatDateTime = DatetimeUtil.formatDateTime
const formatDayLabel = DatetimeUtil.formatDayLabel
const formatTime = DatetimeUtil.formatTime

const expanded = ref(false)
const opened = reactive(new Set<string>())

const visibleEntries = computed<WatchEvent[]>(() =>
    expanded.value || props.paged ? props.entries : props.entries.slice(0, INITIAL_ENTRIES))

const hiddenCount = computed<number>(() => props.entries.length - visibleEntries.value.length)

/** The visible entries in runs of one calendar day, newest day first. */
const days = computed(() => {
  const out: { key: string, label: string, rows: Row[] }[] = []
  visibleEntries.value.forEach(entry => {
    const key = new Date(entry['@timestamp']).toDateString()
    let day = out[out.length - 1]
    if (!day || day.key !== key) {
      day = { key, label: formatDayLabel(entry['@timestamp']), rows: [] }
      out.push(day)
    }
    day.rows.push(rowOf(entry))
  })
  return out
})

function rowOf(entry: WatchEvent): Row {
  const headline = headlineOf(entry)
  return {
    // A record's writes are stamped strictly later than every earlier one, so its time names an entry
    // whatever entries are added around it
    key: `${entry['@timestamp']}|${entry.type}|${entry.scope ?? ''}|${entry.id}`,
    entry,
    recordLabel: entry.name ?? entry.id,
    link: props.recordLink?.(entry) ?? null,
    headline,
    commit: phaseOf(entry) ? commitOf(entry) : null,
    reason: reasonOf(entry, headline),
    tone: toneOf(entry, headline)
  }
}

function headlineOf(entry: WatchEvent): Headline {
  let ret: Headline
  const phase = phaseOf(entry)
  const status = statusOf(entry)
  if (phase) {
    ret = { lead: entry.eventType === WatchEventType.OBSERVED_REPORTED ? 'Reported' : 'Asked for', word: phase, trail: '' }
  } else if (entry.eventType === WatchEventType.CONDITION_SET) {
    ret = { lead: 'Marked', word: conditionOf(entry), trail: '' }
  } else if (entry.eventType === WatchEventType.CONDITION_CLEARED) {
    ret = { lead: 'Cleared', word: conditionOf(entry), trail: '' }
  } else if (entry.eventType === WatchEventType.STATUS_CHANGED && status) {
    const at = entry.message.indexOf(status)
    ret = at >= 0
        ? { lead: entry.message.slice(0, at).trim(), word: status, trail: entry.message.slice(at + status.length).trim() }
        : { lead: 'Run', word: status, trail: '' }
  } else {
    ret = { lead: entry.message, word: null, trail: '' }
  }
  return ret
}

/** Why the entry happened, where the headline does not already say it: a failure's error, or a condition's message. */
function reasonOf(entry: WatchEvent, headline: Headline): string | null {
  const error = (entry.value as { error?: unknown } | null)?.error
  let ret: string | null
  if (typeof error === 'string' && error) {
    ret = withoutExceptionPrefix(error)
  } else if (entry.eventType === WatchEventType.CONDITION_SET
      || (headline.word && !entry.message.includes(headline.word))) {
    ret = entry.message || null
  } else {
    ret = null
  }
  return ret
}

/** The phase a desired or observed state names, null for an entry that writes none. */
function phaseOf(entry: WatchEvent): string | null {
  const phase = (entry.value as { phase?: unknown } | null)?.phase
  return (entry.eventType === WatchEventType.OBSERVED_REPORTED || entry.eventType === WatchEventType.DESIRED_UPDATED
      || entry.eventType === WatchEventType.DESIRED_RENEWED) && typeof phase === 'string' ? phase : null
}

/** The status a status change wrote: the value itself, or its status field. */
function statusOf(entry: WatchEvent): string | null {
  const value = entry.value as { status?: unknown } | string | null
  const status = typeof value === 'string' ? value : value?.status
  return typeof status === 'string' ? status : null
}

function conditionOf(entry: WatchEvent): string | null {
  const type = (entry.value as { type?: unknown } | null)?.type
  return typeof type === 'string' ? type : null
}

function commitOf(entry: WatchEvent): string | null {
  const commitSha = (entry.value as { commitSha?: unknown } | null)?.commitSha
  return typeof commitSha === 'string' ? commitSha : null
}

function toneOf(entry: WatchEvent, headline: Headline): Tone {
  let ret: Tone
  if (entry.eventType === WatchEventType.DELETION_REQUESTED) {
    ret = 'danger'
  } else if (entry.eventType === WatchEventType.CONDITION_SET) {
    ret = 'warn'
  } else if (entry.eventType === WatchEventType.CONDITION_CLEARED) {
    ret = 'success'
  } else {
    const word = headline.word ?? ''
    if (FAILED_WORDS.has(word)) {
      ret = 'danger'
    } else if (DONE_WORDS.has(word)) {
      ret = 'success'
    } else if (ACTIVE_WORDS.has(word)) {
      ret = 'info'
    } else {
      ret = 'neutral'
    }
  }
  return ret
}

/** Everything the ledger holds for the entry beside its value, one labelled line each. */
function detailsOf(entry: WatchEvent): { label: string, value: string }[] {
  const facts: { label: string, value: string | null }[] = [
    { label: 'When', value: formatDateTime(entry['@timestamp']) },
    { label: 'What', value: entry.eventType.toLowerCase().replace(/_/g, ' ') },
    { label: 'Message', value: entry.message },
    { label: 'Record', value: `${RECORD_LABELS[entry.type] ?? entry.type} ${entry.id}` },
    { label: 'Name', value: entry.name },
    { label: 'Organization', value: entry.scope },
    { label: 'Belongs to', value: entry.parent },
    { label: 'Caused by', value: entry.source },
    { label: 'Written by', value: entry.serverNodeId },
    { label: 'Generation', value: entry.generation !== null && entry.generation !== undefined ? String(entry.generation) : null }
  ]
  return facts.filter((fact): fact is { label: string, value: string } => !!fact.value)
}

function toggle(key: string) {
  if (opened.has(key)) {
    opened.delete(key)
  } else {
    opened.add(key)
  }
}
</script>
