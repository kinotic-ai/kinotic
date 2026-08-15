<template>
  <div class="rounded-lg border border-surface p-4">
    <div class="flex items-start justify-between">
      <div>
        <h2 class="text-base font-semibold">Workloads</h2>
        <p class="mb-4 text-xs text-muted-color">{{ description }}</p>
      </div>
      <RouterLink v-if="viewAllTo" :to="viewAllTo" class="text-sm text-muted-color hover:text-color">View all</RouterLink>
    </div>
    <div v-if="total === 0" class="py-6 text-center text-sm text-muted-color">
      No workloads
    </div>
    <template v-else>
      <!-- vue-echarts sizes from inline style, so the fixed height lives on a wrapper -->
      <div class="h-8 w-full">
        <VChart style="height: 100%; width: 100%;" :option="chartOption" autoresize />
      </div>
      <div class="mt-3 flex flex-wrap gap-x-4 gap-y-1">
        <div v-for="seg in segments" :key="seg.label" class="flex items-center gap-1.5 text-sm">
          <span class="h-2.5 w-2.5 rounded-full" :style="{ background: seg.color }" />
          <span class="text-muted-color">{{ seg.label }}</span>
          <span class="font-medium">{{ seg.count }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import VChart from 'vue-echarts'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import { WorkloadStatus } from '@kinotic-ai/os-api'
import { isDark } from '@kinotic-ai/frontend-common'

import '@/charts'

type StatusBucket = 'running' | 'starting' | 'stopping' | 'stopped' | 'failed'

// Theme ramp steps validated for adjacent-pair CVD separation and surface contrast in both
// modes (dark uses the lighter 400 steps). STOPPED is a deliberate achromatic neutral, and
// every segment carries a labeled legend row, so identity is never color alone.
const STATUS_SERIES: { bucket: StatusBucket; label: string; light: string; dark: string }[] = [
  { bucket: 'running', label: 'Running', light: '#22C55E', dark: '#4ADE80' },
  { bucket: 'starting', label: 'Starting', light: '#0EA5E9', dark: '#38BDF8' },
  { bucket: 'stopping', label: 'Stopping', light: '#F97316', dark: '#FB923C' },
  { bucket: 'stopped', label: 'Stopped', light: '#6B7280', dark: '#9CA3AF' },
  { bucket: 'failed', label: 'Failed', light: '#EF4444', dark: '#F87171' }
]

const BUCKET_BY_STATUS: Record<WorkloadStatus, StatusBucket> = {
  [WorkloadStatus.RUNNING]: 'running',
  [WorkloadStatus.PENDING]: 'starting',
  [WorkloadStatus.STARTING]: 'starting',
  [WorkloadStatus.STOPPING]: 'stopping',
  [WorkloadStatus.STOPPED]: 'stopped',
  [WorkloadStatus.FAILED]: 'failed'
}

/**
 * The workload state breakdown: a stacked bar over a labeled legend, for the whole platform
 * or one organization's workloads when organizationId is set.
 */
const props = defineProps<{
  description: string
  organizationId?: string
  viewAllTo?: RouteLocationRaw
}>()

const counts = ref<Record<StatusBucket, number>>({ running: 0, starting: 0, stopping: 0, stopped: 0, failed: 0 })

const segments = computed(() => STATUS_SERIES.map(series => ({
  label: series.label,
  count: counts.value[series.bucket],
  color: isDark.value ? series.dark : series.light
})))

const total = computed(() => segments.value.reduce((sum, seg) => sum + seg.count, 0))

// One stacked horizontal bar; the surface-colored border draws the gap between segments.
// The legend stays in HTML below the chart, where it can carry the counts.
const chartOption = computed(() => {
  const surface = isDark.value ? '#171717' : '#ffffff'
  return {
    animationDuration: 300,
    grid: { left: 0, right: 0, top: 0, bottom: 0 },
    xAxis: { type: 'value', show: false, max: total.value },
    yAxis: { type: 'category', show: false, data: [''] },
    tooltip: { trigger: 'item', confine: true },
    series: segments.value.filter(seg => seg.count > 0).map(seg => ({
      name: seg.label,
      type: 'bar',
      stack: 'states',
      barWidth: 12,
      data: [seg.count],
      itemStyle: { color: seg.color, borderColor: surface, borderWidth: 1, borderRadius: 2 }
    }))
  }
})

async function load() {
  const next: Record<StatusBucket, number> = { running: 0, starting: 0, stopping: 0, stopped: 0, failed: 0 }
  try {
    // Buckets are counted client-side from pages; the loop is bounded, so a platform with
    // more than 1000 workloads undercounts — revisit with server-side aggregation then
    const pageSize = 100
    for (let pageNumber = 0; pageNumber < 10; pageNumber++) {
      const page = await Kinotic.workloads.findAll(Pageable.create(pageNumber, pageSize))
      const content = page.content ?? []
      for (const workload of content) {
        if (!props.organizationId || workload.organizationId === props.organizationId) {
          next[BUCKET_BY_STATUS[workload.status]] += 1
        }
      }
      if (content.length < pageSize) {
        break
      }
    }
  } catch {
    // The empty state stands in when the platform can't answer
  }
  counts.value = next
}

// The header's organization switcher navigates in place; refetch for the new organization
watch(() => props.organizationId, load)

onMounted(load)
</script>
