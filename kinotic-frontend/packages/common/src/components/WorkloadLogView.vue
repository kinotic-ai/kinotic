<template>
  <LogView :source="source" :run="run" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Kinotic } from '@kinotic-ai/core'
import LogView from './LogView.vue'
import type { LogSource } from './LogSource'
import type { WorkloadRun } from './WorkloadRun'

const props = defineProps<{
  /** The organization the workload ran for; null for a platform workload, which only the system console reads. */
  organizationId: string | null
  workloadId: string
  /** The window the workload ran over, when the caller knows it; see LogView. */
  run?: WorkloadRun
}>()

const source = computed<LogSource>(() => ({
  history: (start, end, limit) => Kinotic.logs.history({
    organizationId: props.organizationId,
    workloadId: props.workloadId,
    start,
    end,
    limit
  }),
  tail: () => Kinotic.logs.tail(props.organizationId, props.workloadId)
}))
</script>
