<template>
  <div class="flex flex-col gap-4">
    <div class="grid gap-4 lg:grid-cols-3">
      <MetricChart
        title="Requests"
        description="Calls per second, by service."
        :series="requests.series"
        :loading="requests.loading"
        :error="requests.error"
        :format="formatRate"
      />
      <MetricChart
        title="Errors"
        description="Share of calls that failed, by service."
        :series="errors.series"
        :loading="errors.loading"
        :error="errors.error"
        :format="formatPercent"
      />
      <MetricChart
        title="Latency"
        description="95th percentile call duration, by service."
        :series="latency.series"
        :loading="latency.loading"
        :error="latency.error"
        :format="formatSeconds"
      />
    </div>

    <div class="flex flex-col gap-3">
      <div class="flex items-center gap-2">
        <InputText
          v-model="customQuery"
          class="flex-1 font-mono text-sm"
          placeholder='PromQL over this tenant, e.g. sum by (service) (rate(traces_spanmetrics_calls_total[5m]))'
          @keyup.enter="runCustom"
        />
        <Button label="Run" icon="pi pi-play" size="small" :loading="custom.loading" :disabled="!customQuery.trim()" @click="runCustom" />
      </div>
      <MetricChart
        v-if="customRan"
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
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'

import MetricChart from './MetricChart.vue'
import type { MetricSeries } from './MetricSeries'
import type { TimeRange } from './TimeRange'
import { queryMetrics, redQueries } from './telemetryApi'
import { formatDuration, formatPercent, formatRate } from './telemetryDisplay'

/**
 * The RED metrics of the organization's services — or of one application's — over the given
 * range, and a free PromQL query over the same tenant beneath them.
 */
const props = defineProps<{
  organizationId: string | null
  applicationId: string | null
  range: TimeRange
}>()

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
const customRan = ref(false)

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
    target.error = err instanceof Error ? err.message : 'Failed to query metrics'
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
    customRan.value = true
    load(custom, customQuery.value.trim())
  }
}

// The range object is replaced on every refresh, so a refresh reloads even an unchanged preset
watch(() => [props.organizationId, props.applicationId, props.range], () => {
  loadRed()
  if (customRan.value) {
    runCustom()
  }
}, { immediate: true })
</script>
