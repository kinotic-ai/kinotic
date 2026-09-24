<template>
  <div class="flex flex-col gap-4">
    <div class="flex flex-wrap items-center gap-3">
      <Select
        v-model="presetMs"
        :options="TIME_RANGE_PRESETS"
        option-label="label"
        option-value="ms"
        size="small"
        class="w-48"
      />
      <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined size="small" @click="refresh" />
      <span class="text-xs text-muted-color">{{ formatDateFromEpoch(range.start) }} — {{ formatDateFromEpoch(range.end) }}</span>
    </div>

    <section v-for="group in groups" :key="group.title">
      <h3 class="mb-2 text-sm font-semibold">{{ group.title }}</h3>
      <div class="grid gap-4 lg:grid-cols-3">
        <MetricChart
          v-for="chart in group.charts"
          :key="chart.title"
          :title="chart.title"
          :description="chart.description"
          :series="states[chart.title]?.series ?? []"
          :loading="states[chart.title]?.loading ?? true"
          :error="states[chart.title]?.error ?? null"
          :format="chart.format"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import Button from 'primevue/button'
import Select from 'primevue/select'

import { DatetimeUtil, MetricChart, TIME_RANGE_PRESETS, errorMessage, formatBytes, formatDuration, formatPercent,
         formatRate, queryMetrics, rangeEndingNow, type MetricSeries, type TimeRange } from '@kinotic-ai/frontend-common'

import { serverQueries } from '@/util/serverMetrics'

/**
 * The kinotic-server's own runtime over a chosen time range, read from the platform's tenant:
 * its JVM's CPU, memory, garbage collection, threads and direct buffers, and the HTTP requests
 * it answers with their errors and latency.
 */

/** A series a chart draws: a named query draws one series under that name, an unnamed one a series per label value. */
interface ChartQuery {
  name?: string
  query: string
}

/** One chart: what it shows, the unit its values are in, and the queries it draws. */
interface ChartSpec {
  title: string
  description: string
  format: (value: number) => string
  queries: ChartQuery[]
}

/** What a chart has drawn so far. */
interface ChartState {
  series: MetricSeries[]
  loading: boolean
  error: string | null
}

const formatDateFromEpoch = DatetimeUtil.formatDateFromEpoch
const formatCount = (value: number) => String(Math.round(value))
const formatSeconds = (seconds: number) => formatDuration(seconds * 1000)

const presetMs = ref<number>(TIME_RANGE_PRESETS[1]!.ms)
const range = ref<TimeRange>(rangeEndingNow(presetMs.value))
const states = reactive<Record<string, ChartState>>({})

const groups = computed<Array<{ title: string; charts: ChartSpec[] }>>(() => {
  const queries = serverQueries(range.value)
  return [
    {
      title: 'JVM',
      charts: [
        {
          title: 'CPU',
          description: 'Share of its host\'s CPU each server process uses.',
          format: formatPercent,
          queries: [{ query: queries.cpuByServer }]
        },
        {
          title: 'Memory',
          description: 'Heap in use against its limit, and non-heap, across the servers.',
          format: formatBytes,
          queries: [
            { name: 'Heap used', query: queries.heapUsed },
            { name: 'Heap limit', query: queries.heapLimit },
            { name: 'Non-heap used', query: queries.nonHeapUsed }
          ]
        },
        {
          title: 'Garbage collection',
          description: 'Share of time spent collecting, by collector.',
          format: formatPercent,
          queries: [{ query: queries.gcTime }]
        },
        {
          title: 'Threads',
          description: 'Live threads across the servers.',
          format: formatCount,
          queries: [{ name: 'Live', query: queries.threads }]
        },
        {
          title: 'Blocked threads',
          description: 'Threads waiting on a lock another thread holds.',
          format: formatCount,
          queries: [{ name: 'Blocked', query: queries.blockedThreads }]
        },
        {
          title: 'Direct buffers',
          description: 'Off-heap memory Vert.x and Netty hold, by pool.',
          format: formatBytes,
          queries: [{ query: queries.directBuffers }]
        }
      ]
    },
    {
      title: 'HTTP',
      charts: [
        {
          title: 'Requests',
          description: 'HTTP requests the servers answer per second.',
          format: formatRate,
          queries: [{ name: 'All routes', query: queries.httpRequests }]
        },
        {
          title: 'Server errors',
          description: 'Share of HTTP requests answered with a 5xx.',
          format: formatPercent,
          queries: [{ name: '5xx', query: queries.httpServerErrors }]
        },
        {
          title: 'Latency',
          description: '95th percentile HTTP response time.',
          format: formatSeconds,
          queries: [{ name: 'p95', query: queries.httpLatencyP95 }]
        }
      ]
    }
  ]
})

async function draw(chart: ChartSpec) {
  if (!states[chart.title]) {
    states[chart.title] = { series: [], loading: false, error: null }
  }
  // Read back through the reactive record: the object assigned above is the raw one, whose
  // changes would not redraw the chart
  const state = states[chart.title]!
  state.loading = true
  state.error = null
  try {
    const drawn = await Promise.all(chart.queries.map(async ({ name, query }) => {
      const series = await queryMetrics(null, query, range.value)
      return name ? series.map(entry => ({ ...entry, name })) : series
    }))
    state.series = drawn.flat()
  } catch (err) {
    state.series = []
    state.error = errorMessage(err, 'Failed to query metrics')
  } finally {
    state.loading = false
  }
}

// A new range object each time, which is what redraws the charts
function refresh() {
  range.value = rangeEndingNow(presetMs.value)
}

watch(presetMs, refresh)
watch(groups, all => all.forEach(group => group.charts.forEach(draw)), { immediate: true })
</script>
