<template>
  <WorkloadLogView :key="data.workloadId" :organization-id="run.organizationId" :workload-id="data.workloadId"
                   :run="{ started: node.started, finished: node.finished }" />
</template>

<script setup lang="ts">
import type { JobRun, WorkloadLog } from '@kinotic-ai/management-api'
import type { JobTaskNode } from './grind/JobTaskNode'
import WorkloadLogView from './WorkloadLogView.vue'

/**
 * The TaskViews.WORKLOAD_LOG view: the console log of the workload the task runs, read in the
 * organization that owns the run, over the task's own span.
 */
defineProps<{
  data: WorkloadLog
  node: JobTaskNode
  run: JobRun
}>()
</script>
