<template>
  <div class="rounded-lg border border-surface p-4">
    <div class="mb-3 flex items-start justify-between gap-3">
      <div>
        <h2 class="text-base font-semibold">{{ title }}</h2>
        <p class="text-xs text-muted-color">{{ description }}</p>
      </div>
      <RouterLink v-if="viewAllTo" :to="viewAllTo" class="whitespace-nowrap text-sm text-muted-color hover:text-color">View all</RouterLink>
    </div>
    <div v-if="rows.length === 0" class="py-6 text-center text-sm text-muted-color">
      No workload is holding capacity
    </div>
    <div v-else class="flex flex-col gap-3">
      <div v-for="row in rows" :key="row.owner">
        <div class="mb-1 flex justify-between gap-3 text-sm">
          <RouterLink v-if="row.to" :to="row.to" class="min-w-0 truncate font-mono hover:underline">{{ row.owner }}</RouterLink>
          <span v-else class="min-w-0 truncate font-mono text-muted-color">{{ row.owner }}</span>
          <span class="whitespace-nowrap tabular-nums">{{ row.text }}</span>
        </div>
        <CapacityBar :pct="row.pct" :color="color" :tooltip="row.tooltip" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import { accentColor } from '../../charts/chartTheme'
import { isDark } from '../../composables/useTheme'
import { formatCpus, formatMb } from '../../util/helpers'
import CapacityBar from '../dashboard/CapacityBar.vue'
import { formatAllocation, type Allocation } from './workloadAllocation'

/** How many owners the card names before it folds the rest into one row. */
const MAX_ROWS = 5

/**
 * What each owner's running workloads hold of the nodes, largest first: one bar per owner,
 * its CPU against the largest owner's, with its CPU and memory beside it. Owners past the
 * first few fold into an Other row.
 */
const props = defineProps<{
  title: string
  description: string
  allocations: Allocation[]
  /** Where an owner's row leads; an owner without one is not a link. */
  ownerRoute?: (owner: string) => RouteLocationRaw | undefined
  viewAllTo?: RouteLocationRaw
}>()

// One series, so every bar wears the same accent; the length carries the magnitude
const color = computed(() => accentColor('sky', isDark.value))

const rows = computed(() => {
  const shown = props.allocations.slice(0, MAX_ROWS)
  const rest = props.allocations.slice(MAX_ROWS)
  const all: Array<Allocation & { to?: RouteLocationRaw }> = shown.map(allocation => ({ ...allocation, to: props.ownerRoute?.(allocation.owner) }))
  if (rest.length > 0) {
    all.push(rest.reduce((sum, allocation) => ({
      ...sum,
      workloads: sum.workloads + allocation.workloads,
      cpus: sum.cpus + allocation.cpus,
      memoryMb: sum.memoryMb + allocation.memoryMb,
      diskMb: sum.diskMb + allocation.diskMb
    }), { owner: `${rest.length} other${rest.length === 1 ? '' : 's'}`, workloads: 0, cpus: 0, memoryMb: 0, diskMb: 0 }))
  }
  const max = Math.max(...all.map(allocation => allocation.cpus), 0)
  return all.map(allocation => ({
    ...allocation,
    pct: max > 0 ? Math.round((allocation.cpus / max) * 100) : 0,
    text: formatAllocation(allocation),
    tooltip: `${allocation.owner}: ${formatCpus(allocation.cpus)} CPU, ${formatMb(allocation.memoryMb)} memory, `
             + `${formatMb(allocation.diskMb)} disk across ${allocation.workloads} workload${allocation.workloads === 1 ? '' : 's'}`
  }))
})
</script>
