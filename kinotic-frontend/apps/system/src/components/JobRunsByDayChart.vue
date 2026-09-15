<template>
  <div class="rounded-lg border border-surface p-4">
    <div class="mb-3 flex items-start justify-between gap-3">
      <div>
        <h2 class="text-base font-semibold">Job runs, last {{ days }} days</h2>
        <p class="text-xs text-muted-color">Per day, and how many failed.</p>
      </div>
      <RouterLink v-if="viewAllTo" :to="viewAllTo" class="whitespace-nowrap text-sm text-muted-color hover:text-color">View all</RouterLink>
    </div>
    <div v-if="runs.length === 0" class="flex h-44 items-center justify-center text-sm text-muted-color">
      No runs in the last {{ days }} days
    </div>
    <!-- vue-echarts sizes from inline style, so the fixed height lives on a wrapper -->
    <div v-else class="h-44 w-full">
      <VChart style="height: 100%; width: 100%;" :option="option" autoresize />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import VChart from 'vue-echarts'

import { ExecutionStatus, type JobRun } from '@kinotic-ai/management-api'
import { DatetimeUtil, accentColor, chartGridColor, chartLegend, chartTextColor, isDark } from '@kinotic-ai/frontend-common'

const DAY_MS = 24 * 60 * 60 * 1000

/**
 * How many runs started on each of the last {@code days} days, stacked by outcome: the ones
 * that completed, and the ones that failed. Runs still going or cancelled are not drawn.
 */
const props = withDefaults(defineProps<{
  runs: JobRun[]
  days?: number
  viewAllTo?: RouteLocationRaw
}>(), { days: 7 })

const buckets = computed(() => {
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  const firstDay = start.getTime() - (props.days - 1) * DAY_MS
  const ret = Array.from({ length: props.days }, (_, i) => {
    const day = new Date(firstDay + i * DAY_MS)
    return { label: day.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric' }), completed: 0, failed: 0 }
  })
  for (const run of props.runs) {
    const started = DatetimeUtil.toEpochMillis(run.started)
    if (started === null) continue
    const index = Math.floor((started - firstDay) / DAY_MS)
    const bucket = ret[index]
    if (!bucket) continue
    if (run.status === ExecutionStatus.COMPLETED) {
      bucket.completed += 1
    } else if (run.status === ExecutionStatus.FAILED) {
      bucket.failed += 1
    }
  }
  return ret
})

const option = computed(() => {
  const dark = isDark.value
  const surface = dark ? '#171717' : '#ffffff'
  // Zero-count days carry null data: nothing draws, but the day keeps its slot on the axis
  const series = (name: string, key: 'completed' | 'failed', color: string) => ({
    name,
    type: 'bar',
    stack: 'runs',
    barMaxWidth: 28,
    data: buckets.value.map(bucket => bucket[key] > 0 ? bucket[key] : null),
    itemStyle: { color, borderColor: surface, borderWidth: 1, borderRadius: 2 }
  })
  return {
    animationDuration: 300,
    // containLabel keeps the axis labels inside the grid's box; the bottom margin is the legend's row
    grid: { left: 8, right: 16, top: 12, bottom: 36, containLabel: true },
    tooltip: { trigger: 'axis', confine: true },
    legend: chartLegend(dark),
    xAxis: {
      type: 'category',
      data: buckets.value.map(bucket => bucket.label),
      axisLine: { lineStyle: { color: chartGridColor(dark) } },
      axisTick: { show: false },
      axisLabel: { color: chartTextColor(dark), fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: chartGridColor(dark) } },
      axisLabel: { color: chartTextColor(dark), fontSize: 11 }
    },
    series: [
      series('Completed', 'completed', accentColor('green', dark)),
      series('Failed', 'failed', accentColor('red', dark))
    ]
  }
})
</script>
