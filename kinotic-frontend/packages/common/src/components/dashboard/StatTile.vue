<template>
  <component
    :is="to ? RouterLink : 'div'"
    :to="to"
    class="flex flex-col gap-1 rounded-lg border border-surface p-4"
    :class="to ? 'cursor-pointer text-color no-underline transition-colors hover:bg-emphasis' : ''"
  >
    <div class="flex items-start justify-between gap-2">
      <span class="text-xs font-medium uppercase tracking-wide text-muted-color">{{ label }}</span>
      <span
        v-if="icon"
        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
        :style="{ background: accentStyle.tint }"
      >
        <i :class="['pi', icon]" :style="{ color: accentStyle.icon, fontSize: '0.9rem' }" />
      </span>
    </div>
    <span v-if="tag" class="py-1">
      <Tag :value="value" :severity="tag" />
    </span>
    <span v-else class="text-3xl font-semibold">{{ value }}</span>
    <!-- vue-echarts sizes from inline style, so the fixed height lives on a wrapper -->
    <div v-if="trend && trend.length > 1" class="h-8 w-full">
      <VChart style="height: 100%; width: 100%;" :option="trendOption" autoresize />
    </div>
    <span class="text-xs text-muted-color">{{ description }}</span>
  </component>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import Tag from 'primevue/tag'
import VChart from 'vue-echarts'

import { accentColor } from '../../charts/chartTheme'
import { isDark } from '../../composables/useTheme'
import DatetimeUtil from '../../util/DatetimeUtil'
import type { Stat } from './Stat'

/**
 * One dashboard statistic: a labeled value with a caption explaining what it measures.
 * Given a route it becomes a link with hover affordance; given a tag severity the value
 * renders as a Tag instead of a number; given an icon and accent it wears a tinted icon
 * chip — the value itself always stays in text ink; given a trend it draws the value's
 * recent course as a sparkline in the accent.
 */
const props = defineProps<Stat>()

function withAlpha(hex: string, alpha: number): string {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

// The accent for the icon, and the same hue at low alpha for the chip behind it
const accentStyle = computed(() => {
  const color = accentColor(props.accent ?? 'sky', isDark.value)
  return { icon: color, tint: withAlpha(color, isDark.value ? 0.16 : 0.12) }
})

const trendOption = computed(() => {
  const color = accentStyle.value.icon
  const format = props.format ?? ((value: number) => String(value))
  return {
    animationDuration: 300,
    grid: { left: 0, right: 0, top: 2, bottom: 2 },
    xAxis: { type: 'time', show: false },
    yAxis: { type: 'value', show: false, min: 0 },
    tooltip: {
      trigger: 'axis',
      confine: true,
      formatter: (params: Array<{ value: [number, number] }>) => {
        const point = params[0]?.value
        return point ? `${DatetimeUtil.formatTime(point[0])}<br/><b>${format(point[1])}</b>` : ''
      }
    },
    series: [{
      type: 'line',
      showSymbol: false,
      lineStyle: { width: 2, color },
      itemStyle: { color },
      areaStyle: { color: accentStyle.value.tint },
      data: props.trend
    }]
  }
})
</script>
