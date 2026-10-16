<template>
  <div v-if="data.generated !== null" class="mb-2 text-xs text-muted-color">
    {{ data.generated ? 'Generated the SBOM' : 'The dependencies are unchanged' }}
  </div>
  <WorkloadLogView v-if="data.generated !== false" :key="data.workloadId" :organization-id="run.organizationId"
                   :workload-id="data.workloadId" :run="{ started: node.started, finished: node.finished }" />
</template>

<script setup lang="ts">
import type { JobRun, SbomGeneration } from '@kinotic-ai/management-api'
import type { JobTaskNode } from '../grind/JobTaskNode'
import WorkloadLogView from '../WorkloadLogView.vue'

/**
 * The TaskViews.SBOM_GENERATION view: the log of the workload generating the SBOM, and once the
 * step completes, whether the run generated it or kept one whose dependencies were the same.
 */
defineProps<{
  data: SbomGeneration
  node: JobTaskNode
  run: JobRun
}>()
</script>
