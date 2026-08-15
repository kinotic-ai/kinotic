<template>
  <!-- vue-echarts sizes from inline style, so the fixed height lives on the wrapper -->
  <div class="h-3 w-full">
    <VChart style="height: 100%; width: 100%;" :option="option" autoresize />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'

import { isDark } from '@kinotic-ai/frontend-common'

import '@/charts'

/** A single allocation gauge: the percentage fills a surface-colored track. */
const props = defineProps<{
  pct: number
}>()

const option = computed(() => {
  const track = isDark.value ? '#27272A' : '#E5E5E7'
  const fill = isDark.value ? '#38BDF8' : '#0EA5E9'
  return {
    animationDuration: 300,
    grid: { left: 0, right: 0, top: 0, bottom: 0 },
    xAxis: { type: 'value', show: false, max: 100 },
    yAxis: { type: 'category', show: false, data: [''] },
    tooltip: { trigger: 'item', confine: true, formatter: () => `${props.pct}% allocated` },
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
