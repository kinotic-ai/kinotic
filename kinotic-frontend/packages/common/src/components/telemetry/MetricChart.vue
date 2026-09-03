<template>
  <div class="rounded-lg border border-surface p-4">
    <h3 class="text-sm font-semibold">{{ title }}</h3>
    <p class="text-xs text-muted-color">{{ description }}</p>
    <Message v-if="error" severity="error" :closable="false" class="mt-3">{{ error }}</Message>
    <div v-else-if="!loading && series.length === 0" class="flex h-56 items-center justify-center text-sm text-muted-color">
      No data in this range
    </div>
    <!-- vue-echarts sizes from inline style, so the fixed height lives on a wrapper -->
    <div v-else class="mt-2 h-56 w-full">
      <VChart style="height: 100%; width: 100%;" :option="option" autoresize />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Message from 'primevue/message'
import VChart from 'vue-echarts'

import { isDark } from '../../composables/useTheme'
import type { MetricSeries } from './MetricSeries'
import { formatTime, seriesColor } from './telemetryDisplay'
import './charts'

/**
 * One time-series chart of a metric query: a line per series over the queried range, with the
 * value axis and tooltip formatted for the metric's unit.
 */
const props = defineProps<{
  title: string
  description: string
  series: MetricSeries[]
  loading: boolean
  error: string | null
  /** Renders a value in the metric's unit, on the axis and in the tooltip. */
  format: (value: number) => string
}>()

const option = computed(() => {
  // The preset's muted text tokens, so axis and legend text match the card captions
  const mutedText = isDark.value ? '#A1A1AA' : '#71717A'
  const gridLine = isDark.value ? '#27272A' : '#E4E4E7'
  return {
    animationDuration: 300,
    grid: { left: 8, right: 16, top: 12, bottom: 36, containLabel: true },
    xAxis: {
      type: 'time',
      axisLabel: { color: mutedText, formatter: (value: number) => formatTime(value) },
      axisLine: { lineStyle: { color: gridLine } },
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value',
      min: 0,
      axisLabel: { color: mutedText, formatter: (value: number) => props.format(value) },
      splitLine: { lineStyle: { color: gridLine } }
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      valueFormatter: (value: number) => props.format(value)
    },
    legend: {
      bottom: 0,
      left: 0,
      icon: 'circle',
      itemWidth: 10,
      itemHeight: 10,
      itemGap: 16,
      textStyle: { color: mutedText }
    },
    series: props.series.map((entry, index) => ({
      name: entry.name,
      type: 'line',
      showSymbol: false,
      lineStyle: { width: 2 },
      itemStyle: { color: seriesColor(index, isDark.value) },
      data: entry.points
    }))
  }
})
</script>
