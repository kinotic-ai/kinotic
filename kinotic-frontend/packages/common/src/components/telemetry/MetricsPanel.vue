<template>
  <div class="flex flex-col gap-4">
    <div class="grid gap-4 lg:grid-cols-3">
      <MetricChart
        :icon="Activity"
        :tint="tint"
        title="Requests"
        description="Calls per second, by service."
        :series="requests.series"
        :loading="requests.loading"
        :error="requests.error"
        :format="formatRate"
      />
      <MetricChart
        :icon="CircleX"
        :tint="tint"
        title="Errors"
        description="Share of calls that failed, by service."
        :series="errors.series"
        :loading="errors.loading"
        :error="errors.error"
        :format="formatPercent"
      >
        <template #action>
          <Button label="Failed traces" icon="pi pi-arrow-right" icon-pos="right" size="small" severity="secondary" text
                  @click="emit('show-failed-traces')" />
        </template>
      </MetricChart>
      <MetricChart
        :icon="Timer"
        :tint="tint"
        title="Latency"
        description="95th percentile call duration, by service."
        :series="latency.series"
        :loading="latency.loading"
        :error="latency.error"
        :format="formatSeconds"
      />
    </div>

    <div class="flex flex-col gap-3">
      <div>
        <!-- one bar like the TraceQL query: the language's tag, the query, and Run -->
        <div class="flex items-center gap-3 rounded-lg border border-surface-200 bg-surface-50 py-1.5 pl-1.5 pr-1.5 transition-colors focus-within:border-surface-400 dark:border-surface-700 dark:bg-surface-800/60 dark:focus-within:border-surface-500">
          <span class="shrink-0 rounded-md bg-surface-0 px-2 py-1 text-[0.6875rem] font-semibold uppercase tracking-wider text-surface-600 ring-1 ring-surface-200 dark:bg-surface-900 dark:text-surface-300 dark:ring-surface-700">PromQL</span>
          <InputText
            v-model="customQuery"
            aria-label="PromQL query"
            class="min-w-0 flex-1 !border-0 !bg-transparent !px-0 !font-mono !text-[0.8125rem] !shadow-none"
            placeholder="sum by (service) (rate(traces_spanmetrics_calls_total[5m]))"
            @keyup.enter="runCustom"
          />
          <Button label="Run" icon="pi pi-play" size="small" :loading="custom.loading" :disabled="!customQuery.trim()" @click="runCustom" />
        </div>
        <p class="mt-1.5 text-[0.8125rem] text-muted-color">Chart any query over this tenant's metrics. Press Enter to run it.</p>
      </div>
      <MetricChart
        v-if="custom.query"
        :icon="SquareTerminal"
        :tint="tint"
        title="Query"
        :description="custom.query"
        :series="custom.series"
        :loading="custom.loading"
        :error="custom.error"
        :format="formatNumber"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { Activity, CircleX, SquareTerminal, Timer } from '@lucide/vue'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'

import { errorMessage } from '../../util/helpers'
import { TINTS } from '../../util/tints'
import MetricChart from './MetricChart.vue'
import type { MetricSeries } from './MetricSeries'
import type { TimeRange } from './TimeRange'
import { queryMetrics, redQueries } from './telemetryApi'
import { formatDuration, formatPercent, formatRate } from './telemetryDisplay'

/**
 * The RED metrics of the organization's services — or of one application's — over the given
 * range, and a free PromQL query over the same tenant beneath them. Emits show-failed-traces
 * when the user asks to see the traces behind the error rate.
 */
const props = defineProps<{
  organizationId: string | null
  applicationId: string | null
  range: TimeRange
}>()

const emit = defineEmits<{
  (e: 'show-failed-traces'): void
}>()

// The metric cards share the neutral ink of the platform's own telemetry in every scope
const tint = TINTS.ink

interface Panel {
  query: string
  series: MetricSeries[]
  loading: boolean
  error: string | null
}

function panel(): Panel {
  return { query: '', series: [], loading: false, error: null }
}

const requests = reactive(panel())
const errors = reactive(panel())
const latency = reactive(panel())
const custom = reactive(panel())
const customQuery = ref('')

const formatSeconds = (seconds: number) => formatDuration(seconds * 1000)
const formatNumber = (value: number) => Number.isInteger(value) ? String(value) : value.toPrecision(3)

async function load(target: Panel, query: string) {
  target.query = query
  target.loading = true
  target.error = null
  try {
    target.series = await queryMetrics(props.organizationId, query, props.range)
  } catch (err) {
    target.series = []
    target.error = errorMessage(err, 'Failed to query metrics')
  } finally {
    target.loading = false
  }
}

function loadRed() {
  const queries = redQueries(props.applicationId, props.range)
  load(requests, queries.requests)
  load(errors, queries.errors)
  load(latency, queries.latencyP95)
}

function runCustom() {
  if (customQuery.value.trim()) {
    load(custom, customQuery.value.trim())
  }
}

// The panel replaces the range on every refresh and scope change, so it is the one trigger
watch(() => props.range, () => {
  loadRed()
  if (custom.query) {
    load(custom, custom.query)
  }
}, { immediate: true })
</script>
