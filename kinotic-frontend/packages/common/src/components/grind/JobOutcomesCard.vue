<template>
  <div class="rounded-lg border border-surface p-4">
    <div class="mb-3 flex items-start justify-between gap-3">
      <div>
        <h2 class="text-base font-semibold">Job outcomes, last {{ days }} days</h2>
        <p class="text-xs text-muted-color">How the runs that finished ended, and how long a successful one takes.</p>
      </div>
      <RouterLink v-if="viewAllTo" :to="viewAllTo" class="whitespace-nowrap text-sm text-muted-color hover:text-color">View all</RouterLink>
    </div>
    <div v-if="outcomes.finished === 0" class="py-6 text-center text-sm text-muted-color">
      No run finished in the last {{ days }} days
    </div>
    <template v-else>
      <div class="flex flex-col gap-2">
        <div v-for="figure in figures" :key="figure.caption" class="flex items-baseline gap-3">
          <span class="w-24 shrink-0 text-2xl font-semibold tabular-nums">{{ figure.value }}</span>
          <span class="text-sm text-muted-color">{{ figure.caption }}</span>
        </div>
      </div>
      <p class="mt-4 text-xs text-muted-color">
        {{ outcomes.completed }} completed · {{ outcomes.failed }} failed<span v-if="outcomes.cancelled > 0"> · {{ outcomes.cancelled }} cancelled</span>
      </p>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import { ExecutionStatus, type JobRun } from '@kinotic-ai/management-api'

import DatetimeUtil from '../../util/DatetimeUtil'
import { formatDuration } from '../telemetry/telemetryDisplay'

/**
 * The outcomes of the given runs that finished: the share that completed, the median and 95th
 * percentile duration of the completed ones, and how many ended each way.
 */
const props = withDefaults(defineProps<{
  runs: JobRun[]
  days?: number
  viewAllTo?: RouteLocationRaw
}>(), { days: 7 })

// The value at the given quantile of ascending values, by the nearest rank
function quantile(sorted: number[], q: number): number | null {
  return sorted.length > 0 ? sorted[Math.min(sorted.length - 1, Math.ceil(q * sorted.length) - 1)]! : null
}

const outcomes = computed(() => {
  const count = (status: ExecutionStatus) => props.runs.filter(run => run.status === status).length
  const completed = count(ExecutionStatus.COMPLETED)
  const failed = count(ExecutionStatus.FAILED)
  const cancelled = count(ExecutionStatus.CANCELLED)
  const durations = props.runs
      .filter(run => run.status === ExecutionStatus.COMPLETED)
      .map(run => (DatetimeUtil.toEpochMillis(run.finished) ?? 0) - (DatetimeUtil.toEpochMillis(run.started) ?? 0))
      .filter(ms => ms > 0)
      .sort((a, b) => a - b)
  return {
    finished: completed + failed + cancelled,
    completed,
    failed,
    cancelled,
    median: quantile(durations, 0.5),
    p95: quantile(durations, 0.95)
  }
})

const figures = computed(() => {
  const o = outcomes.value
  return [
    { value: `${Math.round((o.completed / o.finished) * 100)}%`, caption: 'completed' },
    { value: o.median === null ? '—' : formatDuration(o.median), caption: 'median successful run' },
    { value: o.p95 === null ? '—' : formatDuration(o.p95), caption: '95th percentile' }
  ]
})
</script>
