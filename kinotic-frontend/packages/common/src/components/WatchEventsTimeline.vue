<template>
  <EmptyChartCharacter v-if="entries.length === 0" class="py-6" :title="emptyText" />
  <div v-else class="px-5 py-4">
    <section v-for="day in days" :key="day.key" class="mb-2 last:mb-0">
      <h3 class="mb-1 text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-color">{{ day.label }}</h3>
      <ol>
        <li v-for="(entry, position) in day.entries" :key="`${entry['@timestamp']}-${entry.id}-${entry.kind}-${position}`"
            class="relative flex gap-3 pb-3.5 last:pb-2">
          <!-- the rail joins each marker to the next; the day's last entry draws none -->
          <span v-if="position < day.entries.length - 1" class="absolute left-[11px] top-7 bottom-1 w-px bg-surface-200 dark:bg-surface-700" aria-hidden="true" />
          <span :class="['relative z-[1] mt-0.5 flex h-[22px] w-[22px] shrink-0 items-center justify-center rounded-md', TONES[toneOf(entry)].marker]">
            <component :is="ICONS[entry.kind] ?? Circle" :size="13" :stroke-width="1.75" aria-hidden="true" />
          </span>
          <div class="min-w-0 flex-1 pt-0.5">
            <p class="text-[0.8125rem] text-surface-900 dark:text-surface-50" v-tooltip.top="entry.message">
              <template v-if="phaseOf(entry)">
                {{ entry.kind === WatchEventKind.OBSERVED_REPORTED ? 'Reported' : 'Asked for' }}
                <span :class="['rounded px-1 font-medium', TONES[toneOf(entry)].text]">{{ phaseOf(entry) }}</span>
                <template v-if="commitOf(entry)">
                  at <span class="font-mono text-[0.8125rem]">{{ shortSha(commitOf(entry)!) }}</span>
                </template>
              </template>
              <template v-else>{{ entry.message }}</template>
            </p>
            <p class="mt-0.5 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color">
              <span v-tooltip.top="formatEpochDateTime(entry['@timestamp'])" class="tabular-nums">{{ formatTime(entry['@timestamp']) }}</span>
              <span aria-hidden="true">·</span>
              <span>{{ kindLabel(entry.kind) }}</span>
              <template v-if="showRecord">
                <span aria-hidden="true">·</span>
                <span>{{ recordKind(entry.type) }}</span>
                <span class="max-w-[18rem] truncate font-mono" v-tooltip.top="entry.id">{{ entry.id }}</span>
              </template>
              <span aria-hidden="true">·</span>
              <span class="font-mono">{{ entry.source }}</span>
            </p>
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
import { computed, markRaw, ref, type Component } from 'vue'
import { Activity, Check, ChevronsDown, Circle, Eye, RefreshCw, Target, Trash2, TriangleAlert } from '@lucide/vue'
import { WatchEventKind, type WatchEvent, type WatchedType } from '@kinotic-ai/management-api'
import DatetimeUtil from '../util/DatetimeUtil'
import { shortSha } from '../util/helpers'
import { TINTS } from '../util/tints'
import EmptyChartCharacter from './EmptyChartCharacter.vue'

/**
 * A record's ledger entries, newest first, as a timeline grouped by day: when each write landed,
 * what it did, why, and what caused it, each marked in the colour of its outcome. A listing that
 * mixes records, a project's deployment history, names the record on each entry.
 */
const props = withDefaults(defineProps<{
  entries: WatchEvent[]
  /** Whether each entry names the record it belongs to, for a listing across records. */
  showRecord?: boolean
  emptyText?: string
}>(), {
  showRecord: false,
  emptyText: 'Nothing has happened to it yet.'
})

type Tone = 'danger' | 'warn' | 'success' | 'info' | 'neutral'

/** How many entries show before the rest are asked for. */
const INITIAL_ENTRIES = 12

// Markers take the same light-tint, strong-icon treatment as every TINTS icon tile
const TONES: Record<Tone, { marker: string, text: string }> = {
  danger: { marker: TINTS.red, text: 'bg-red-50 text-red-700 dark:bg-red-500/15 dark:text-red-300' },
  warn: { marker: 'bg-amber-100 text-amber-600 dark:bg-amber-500/15 dark:text-amber-300', text: 'bg-amber-50 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300' },
  success: { marker: TINTS.green, text: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300' },
  info: { marker: TINTS.sky, text: 'bg-sky-50 text-sky-700 dark:bg-sky-500/15 dark:text-sky-300' },
  neutral: {
    marker: 'bg-surface-100 text-surface-500 dark:bg-surface-800 dark:text-surface-400',
    text: 'bg-surface-100 text-surface-700 dark:bg-surface-800 dark:text-surface-200'
  }
}

const ICONS: Record<string, Component> = {
  [WatchEventKind.CONDITION_SET]: markRaw(TriangleAlert),
  [WatchEventKind.CONDITION_CLEARED]: markRaw(Check),
  [WatchEventKind.STATUS_CHANGED]: markRaw(Activity),
  [WatchEventKind.DESIRED_UPDATED]: markRaw(Target),
  [WatchEventKind.DESIRED_RENEWED]: markRaw(RefreshCw),
  [WatchEventKind.OBSERVED_REPORTED]: markRaw(Eye),
  [WatchEventKind.DELETION_REQUESTED]: markRaw(Trash2)
}

// The words of a phase or run status, by the outcome they mean
const FAILED_WORDS = new Set(['FAILED'])
const DONE_WORDS = new Set(['DEPLOYED', 'READY', 'COMPLETED'])
const ACTIVE_WORDS = new Set(['DEPLOYING', 'RUNNING', 'PENDING', 'PUBLISHING'])
const STATUS_WORD = /\b(FAILED|DEPLOYED|READY|COMPLETED|DEPLOYING|RUNNING|PENDING|PUBLISHING)\b/

const formatEpochDateTime = DatetimeUtil.formatEpochDateTime
const formatRelativeDate = DatetimeUtil.formatRelativeDate
const formatTime = DatetimeUtil.formatTime

const expanded = ref(false)

const visibleEntries = computed<WatchEvent[]>(() =>
    expanded.value ? props.entries : props.entries.slice(0, INITIAL_ENTRIES))

const hiddenCount = computed<number>(() => props.entries.length - visibleEntries.value.length)

/** The visible entries in runs of one calendar day, newest day first. */
const days = computed(() => {
  const out: { key: string, label: string, entries: WatchEvent[] }[] = []
  for (const entry of visibleEntries.value) {
    const key = new Date(entry['@timestamp']).toDateString()
    let day = out[out.length - 1]
    if (!day || day.key !== key) {
      day = { key, label: formatRelativeDate(entry['@timestamp']), entries: [] }
      out.push(day)
    }
    day.entries.push(entry)
  }
  return out
})

/** The phase a desired or observed state names, null for an entry that writes none. */
function phaseOf(entry: WatchEvent): string | null {
  const phase = (entry.value as { phase?: unknown } | null)?.phase
  return (entry.kind === WatchEventKind.OBSERVED_REPORTED || entry.kind === WatchEventKind.DESIRED_UPDATED
      || entry.kind === WatchEventKind.DESIRED_RENEWED) && typeof phase === 'string' ? phase : null
}

function commitOf(entry: WatchEvent): string | null {
  const commitSha = (entry.value as { commitSha?: unknown } | null)?.commitSha
  return typeof commitSha === 'string' ? commitSha : null
}

function toneOf(entry: WatchEvent): Tone {
  let ret: Tone
  if (entry.kind === WatchEventKind.DELETION_REQUESTED) {
    ret = 'danger'
  } else if (entry.kind === WatchEventKind.CONDITION_SET) {
    ret = 'warn'
  } else if (entry.kind === WatchEventKind.CONDITION_CLEARED) {
    ret = 'success'
  } else {
    const word = phaseOf(entry) ?? entry.message.match(STATUS_WORD)?.[1] ?? ''
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

/** The kind as words: CONDITION_SET reads "condition set". */
function kindLabel(kind: WatchEventKind): string {
  return kind.toLowerCase().replace(/_/g, ' ')
}

function recordKind(type: WatchedType): string {
  return type.toLowerCase().replace(/_/g, ' ')
}
</script>
