<template>
  <DashboardSection :icon="Boxes" :tint="TINTS.sky" title="Workloads by state" :count="workloads.length"
                    :description="description" :link-to="viewAllTo">
    <div v-if="workloads.length === 0" class="py-8 text-center text-sm text-muted-color">
      No workloads
    </div>
    <div v-else class="flex flex-col gap-2.5 p-5">
      <div v-for="row in rows" :key="row.state" class="grid grid-cols-[6.5rem_minmax(0,1fr)_2rem] items-center gap-3 text-sm">
        <span class="flex items-center gap-2 text-muted-color">
          <span class="h-2.5 w-2.5 shrink-0 rounded-full" :style="{ background: row.color }" />
          {{ row.label }}
        </span>
        <CapacityBar :pct="row.pct" :color="row.color" :tooltip="`${row.label}: ${row.count}`" />
        <span class="text-right font-semibold tabular-nums">{{ row.count }}</span>
      </div>
    </div>
  </DashboardSection>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import { Boxes } from '@lucide/vue'

import { WorkloadStatus, type Workload } from '@kinotic-ai/management-api'
import { accentColor, DashboardSection, isDark, TINTS, type ChartAccent } from '@kinotic-ai/frontend-common'

import CapacityBar from './CapacityBar.vue'
import { WORKLOAD_STATES, countByStatus, workloadStateLabel } from '@/util/workloads'

// Theme accents validated for adjacent-pair separation in both modes; STOPPED is a deliberate
// achromatic neutral, and every row carries its label and count, so identity is never color alone
const ACCENT_BY_STATE: Record<WorkloadStatus, ChartAccent | null> = {
  [WorkloadStatus.RUNNING]: 'green',
  [WorkloadStatus.STARTING]: 'sky',
  [WorkloadStatus.PENDING]: 'violet',
  [WorkloadStatus.STOPPING]: 'amber',
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
