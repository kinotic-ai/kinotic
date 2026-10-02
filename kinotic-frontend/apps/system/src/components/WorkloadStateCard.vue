<template>
  <DashboardSection :icon="Boxes" :tint="tint" title="Workloads by state" :count="workloads.length"
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
import { accentColor, DashboardSection, isDark, type ChartAccent } from '@kinotic-ai/frontend-common'

import CapacityBar from './CapacityBar.vue'
import { WORKLOAD_STATES, countByStatus, workloadStateLabel } from '@/util/workloads'

// The states take the platform's status colours: green running, sky on the way up or down, red
// failed, and neutral greys for waiting and stopped; every row carries its label and count, so
// identity is never color alone
const ACCENT_BY_STATE: Record<WorkloadStatus, ChartAccent | 'waiting' | 'stopped'> = {
  [WorkloadStatus.RUNNING]: 'green',
  [WorkloadStatus.STARTING]: 'sky',
  [WorkloadStatus.PENDING]: 'waiting',
  [WorkloadStatus.STOPPING]: 'sky',
  [WorkloadStatus.STOPPED]: 'stopped',
  [WorkloadStatus.FAILED]: 'red'
}

// The neutral greys: waiting is the lighter, so it reads as not yet started beside stopped
const NEUTRALS = {
  waiting: { light: '#A1A1AA', dark: '#71717A' },
  stopped: { light: '#52525B', dark: '#A1A1AA' }
}

/** The state breakdown of the given workloads: one bar per state, longest first, with its count. */
const props = defineProps<{
  /** The icon tint of the page it sits on, one of TINTS. */
  tint: string
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
      color: accent === 'waiting' || accent === 'stopped'
        ? NEUTRALS[accent][isDark.value ? 'dark' : 'light']
        : accentColor(accent, isDark.value)
    }
  })
})
</script>
