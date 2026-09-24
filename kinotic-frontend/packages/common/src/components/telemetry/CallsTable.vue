<template>
  <div class="rounded-lg border border-surface">
    <div class="px-4 pt-4 pb-2">
      <h3 class="text-sm font-semibold">Busiest calls</h3>
      <p class="text-xs text-muted-color">
        The {{ TOP_CALLS }} calls made most over this range, the share of them that failed, and their 95th percentile duration.
      </p>
    </div>
    <Message v-if="error" severity="error" :closable="false" class="mx-4 mb-3">{{ error }}</Message>
    <DataTable v-else :value="rows" size="small" class="text-sm" :loading="loading">
      <template #empty>
        <div class="py-6 text-center text-sm text-muted-color">No calls in this range</div>
      </template>
      <Column header="Call">
        <template #body="{ data }">
          <span class="block max-w-[10rem] truncate font-mono text-xs sm:max-w-[28rem]" :title="data.call">{{ data.call }}</span>
          <span class="block text-xs text-muted-color">{{ data.service }}</span>
        </template>
      </Column>
      <Column header="Calls/s" style="width: 6rem">
        <template #body="{ data }"><span class="tabular-nums">{{ formatRate(data.rate) }}</span></template>
      </Column>
      <Column header="Failed" style="width: 6rem">
        <template #body="{ data }"><span class="tabular-nums">{{ formatPercent(data.errors) }}</span></template>
      </Column>
      <Column header="p95" class="hidden sm:table-cell" style="width: 6rem">
        <template #body="{ data }">
          <span class="tabular-nums">{{ data.latency === null ? '—' : formatDuration(data.latency * 1000) }}</span>
        </template>
      </Column>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Message from 'primevue/message'

import { errorMessage } from '../../util/helpers'
import type { MetricSeries } from './MetricSeries'
import type { TimeRange } from './TimeRange'
import { TOP_CALLS, callQueries, latestValue, queryMetrics } from './telemetryApi'
import { formatDuration, formatPercent, formatRate } from './telemetryDisplay'

/** One call of the breakdown: a span name of a service, with its numbers over the range. */
interface CallRow {
  service: string
  call: string
  rate: number
  errors: number
  latency: number | null
}

/**
 * The busiest calls of the organization's services — or of one application's, or one
 * workload's — over the given range, each with the share of it that failed and its 95th
 * percentile duration.
 */
const props = defineProps<{
  organizationId: string | null
  applicationId: string | null
  workloadId?: string | null
  range: TimeRange
}>()

const rows = ref<CallRow[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

function keyOf(series: MetricSeries): string {
  return JSON.stringify([series.labels.service ?? '', series.labels.span_name ?? ''])
}

function valuesByCall(series: MetricSeries[]): Map<string, number> {
  const ret = new Map<string, number>()
  for (const entry of series) {
    const value = latestValue(entry)
    if (value !== null) {
      ret.set(keyOf(entry), value)
    }
  }
  return ret
}

async function load() {
  loading.value = true
  error.value = null
  const queries = callQueries({ applicationId: props.applicationId, workloadId: props.workloadId }, props.range)
  // Each query averages over the whole range, so it is read once, at the range's end
  const at: TimeRange = { start: props.range.end, end: props.range.end }
  try {
    const [rates, errors, latencies] = await Promise.all([
      queryMetrics(props.organizationId, queries.requests, at),
      queryMetrics(props.organizationId, queries.errors, at),
      queryMetrics(props.organizationId, queries.latencyP95, at)
    ])
    const errorsByCall = valuesByCall(errors)
    const latencyByCall = valuesByCall(latencies)
    rows.value = rates.map(series => ({
      service: series.labels.service ?? '',
      call: series.labels.span_name ?? series.name,
      rate: latestValue(series) ?? 0,
      errors: errorsByCall.get(keyOf(series)) ?? 0,
      latency: latencyByCall.get(keyOf(series)) ?? null
    })).sort((a, b) => b.rate - a.rate)
  } catch (err) {
    rows.value = []
    error.value = errorMessage(err, 'Failed to query the calls')
  } finally {
    loading.value = false
  }
}

// The panel replaces the range on every refresh and scope change, so it is the one trigger
watch(() => props.range, load, { immediate: true })
</script>
