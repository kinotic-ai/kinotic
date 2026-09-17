<template>
  <div class="flex flex-col gap-3">
    <div v-for="row in rows" :key="row.label">
      <div class="mb-1 flex justify-between text-sm">
        <span class="text-muted-color">{{ row.label }}</span>
        <span class="tabular-nums">{{ row.text }}</span>
      </div>
      <CapacityBar :pct="row.pct" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatMb } from '@kinotic-ai/frontend-common'

import CapacityBar from './CapacityBar.vue'
import { percentOf, type Capacity } from '@/util/nodes'

/** CPU, memory and disk gauges: allocated over total. */
const props = defineProps<{
  capacity: Capacity
}>()

const rows = computed(() => {
  const c = props.capacity
  return [
    { label: 'CPU', text: `${c.usedCpus} / ${c.cpus} vCPU`, pct: percentOf(c.usedCpus, c.cpus) },
    { label: 'Memory', text: `${formatMb(c.usedMemoryMb)} / ${formatMb(c.memoryMb)}`, pct: percentOf(c.usedMemoryMb, c.memoryMb) },
    { label: 'Disk', text: `${formatMb(c.usedDiskMb)} / ${formatMb(c.diskMb)}`, pct: percentOf(c.usedDiskMb, c.diskMb) }
  ]
})
</script>
