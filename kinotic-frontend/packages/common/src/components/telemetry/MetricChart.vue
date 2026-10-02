<template>
  <DashboardSection :icon="icon" :tint="tint" :title="title" :description="description">
    <div class="relative p-4">
      <!-- A control that acts on what the chart shows, e.g. the way to the traces behind it; it sits
           in the body's corner so the header keeps its width for the title and every card's chart
           starts at the same height -->
      <div v-if="$slots.action" class="absolute right-2 top-2 z-[1]">
        <slot name="action" />
      </div>
      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
      <EmptyChartCharacter v-else-if="!loading && series.length === 0" class="h-56"
                           title="No data in this range" hint="Try a longer time range, or check back once calls come in." />
      <!-- vue-echarts sizes from inline style, so the fixed height lives on a wrapper -->
      <div v-else class="h-56 w-full">
        <VChart style="height: 100%; width: 100%;" :option="option" autoresize />
      </div>
    </div>
  </DashboardSection>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import Message from 'primevue/message'
import VChart from 'vue-echarts'

import { chartGridColor, chartLegend, chartTextColor, chartTooltip, seriesColor } from '../../charts/chartTheme'
import { isDark } from '../../composables/useTheme'
import DatetimeUtil from '../../util/DatetimeUtil'
import DashboardSection from '../DashboardSection.vue'
import EmptyChartCharacter from '../EmptyChartCharacter.vue'
import type { MetricSeries } from './MetricSeries'

/**
 * One time-series chart of a metric query in a dashboard card: a line per series over the queried
 * range, with the value axis and tooltip formatted for the metric's unit.
 */
const props = defineProps<{
  icon: Component
  /** Classes of the icon tile's colour, one of TINTS. */
  tint: string
  title: string
  description: string
  series: MetricSeries[]
  loading: boolean
  error: string | null
  /** Renders a value in the metric's unit, on the axis and in the tooltip. */
  format: (value: number) => string
}>()

const option = computed(() => {
  const dark = isDark.value
  return {
    animationDuration: 300,
    grid: { left: 8, right: 16, top: 12, bottom: 36, containLabel: true },
    xAxis: {
      type: 'time',
      // A narrow chart gets fewer time labels rather than overlapping ones
      axisLabel: { color: chartTextColor(dark), hideOverlap: true, formatter: (value: number) => DatetimeUtil.formatTime(value) },
      axisLine: { lineStyle: { color: chartGridColor(dark) } },
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value',
      min: 0,
      axisLabel: { color: chartTextColor(dark), formatter: (value: number) => props.format(value) },
      splitLine: { lineStyle: { color: chartGridColor(dark) } }
    },
    tooltip: chartTooltip(dark, {
      format: value => props.format(value),
      heading: value => typeof value === 'number' ? DatetimeUtil.formatEpochDateTime(value) : String(value ?? '')
    }),
    legend: chartLegend(dark),
    series: props.series.map((entry, index) => ({
      name: entry.name,
      type: 'line',
      showSymbol: false,
      lineStyle: { width: 2 },
      itemStyle: { color: seriesColor(index, dark) },
      data: entry.points
    }))
  }
})
</script>
