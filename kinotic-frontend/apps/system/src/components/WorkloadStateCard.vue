<template>
  <div class="rounded-lg border border-surface p-4">
    <div class="mb-3 flex items-start justify-between gap-3">
      <div>
        <h2 class="text-base font-semibold">Workloads by state</h2>
        <p class="text-xs text-muted-color">{{ description }}</p>
      </div>
      <RouterLink v-if="viewAllTo" :to="viewAllTo" class="whitespace-nowrap text-sm text-muted-color hover:text-color">View all</RouterLink>
    </div>
    <div v-if="workloads.length === 0" class="py-6 text-center text-sm text-muted-color">
      No workloads
    </div>
    <div v-else class="flex flex-col gap-2">
      <div v-for="row in rows" :key="row.state" class="grid grid-cols-[6.5rem_minmax(0,1fr)_2rem] items-center gap-3 text-sm">
        <span class="flex items-center gap-2 text-muted-color">
          <span class="h-2.5 w-2.5 shrink-0 rounded-full" :style="{ background: row.color }" />
          {{ row.label }}
        </span>
        <CapacityBar :pct="row.pct" :color="row.color" :tooltip="`${row.label}: ${row.count}`" />
        <span class="text-right font-semibold tabular-nums">{{ row.count }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import { WorkloadStatus, type Workload } from '@kinotic-ai/management-api'
import { accentColor, isDark, type ChartAccent } from '@kinotic-ai/frontend-common'

import CapacityBar from './CapacityBar.vue'
import { WORKLOAD_STATES, countByStatus, workloadStateLabel } from '@/util/workloads'

// Theme accents validated for adjacent-pair separation in both modes; STOPPED is a deliberate
// achromatic neutral, and every row carries its label and count, so identity is never color alone
const ACCENT_BY_STATE: Record<WorkloadStatus, ChartAccent | null> = {
  [WorkloadStatus.RUNNING]: 'green',
  [WorkloadStatus.STARTING]: 'sky',
  [WorkloadStatus.PENDING]: 'violet',
  [WorkloadStatus.STOPPING]: 'amber',
  [WorkloadStatus.COMPLETED]: 'teal',
  [WorkloadStatus.STOPPED]: null,
  [WorkloadStatus.FAILED]: 'red'
}

/** The state breakdown of the given workloads: one bar per state, longest first, with its count. */
const props = defineProps<{
  workloads: Workload[]
  description: string
  viewAllTo?: RouteLocationRaw
}>()

const rows = computed(() => {
  const counts = countByStatus(props.workloads)
  const max = Math.max(1, ...WORKLOAD_STATES.map(state => counts[state]))
  return WORKLOAD_STATES.map(state => {
    const accent = ACCENT_BY_STATE[state]
    return {
      state,
      label: workloadStateLabel(state),
      count: counts[state],
      pct: Math.round((counts[state] / max) * 100),
      color: accent ? accentColor(accent, isDark.value) : (isDark.value ? '#9CA3AF' : '#6B7280')
    }
  })
})
</script>
