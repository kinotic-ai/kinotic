<template>
  <!-- vue-echarts sizes from inline style, so the fixed height lives on the wrapper -->
  <div class="h-3 w-full">
    <VChart style="height: 100%; width: 100%;" :option="option" autoresize />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'

import { accentColor, chartItemTooltip, isDark } from '@kinotic-ai/frontend-common'

/**
 * A single gauge: the percentage fills a surface-colored track. An allocation gauge turns
 * amber past 80 % and red past 95 %; given a color it keeps that color at any level, for a
 * bar that measures a count rather than a fill.
 */
const props = defineProps<{
  pct: number
  color?: string
  /** What the hover card says: its heading, and the one row under it, swatched in the bar's colour. */
  hover: { heading: string, name: string, value: string }
}>()

const option = computed(() => {
  const track = isDark.value ? '#27272A' : '#E5E5E7'
  let fill: string
  if (props.color) {
    fill = props.color
  } else if (props.pct >= 95) {
    fill = accentColor('red', isDark.value)
  } else if (props.pct >= 80) {
    fill = accentColor('amber', isDark.value)
  } else {
    fill = accentColor('sky', isDark.value)
  }
  return {
    animationDuration: 300,
    grid: { left: 0, right: 0, top: 0, bottom: 0 },
    xAxis: { type: 'value', show: false, max: 100 },
    yAxis: { type: 'category', show: false, data: [''] },
    tooltip: chartItemTooltip(isDark.value, props.hover.heading, [{ name: props.hover.name, value: props.hover.value, color: fill }]),
    series: [{
      type: 'bar',
      barWidth: 12,
      data: [props.pct],
      showBackground: true,
      backgroundStyle: { color: track, borderRadius: 2 },
      itemStyle: { color: fill, borderRadius: 2 }
    }]
  }
})
</script>
