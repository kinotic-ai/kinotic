<template>
  <template v-if="ProjectDeployStores.hasArtifacts(node)">
    <ProjectArtifactsDetail v-if="artifacts" :artifacts="artifacts" />
    <span v-else class="text-xs text-muted-color">Waiting for the sync workload's artifact report</span>
  </template>
  <template v-else>
    <div v-if="sbom" class="mb-2 text-xs text-muted-color">
      {{ sbomGenerated ? 'Generated the SBOM' : 'The dependencies are unchanged' }}: {{ sbom.componentCount }} components
    </div>
    <template v-if="!sbom || sbomGenerated">
      <WorkloadLogView v-if="workloadId" :key="workloadId" :organization-id="organizationId" :workload-id="workloadId" :run="run" />
      <span v-else class="text-xs text-muted-color">Waiting for the deployment target</span>
    </template>
  </template>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { JobTaskNode } from '../grind/JobTaskNode'
import WorkloadLogView from '../WorkloadLogView.vue'
import type { WorkloadRun } from '../WorkloadRun'
import ProjectArtifactsDetail from './ProjectArtifactsDetail.vue'
import ProjectDeployStores from './ProjectDeployStores'

/**
 * The detail pane of one task row of a project deployment run: the artifacts the run bound,
 * or the log of the workload the task ran, with a placeholder until either is known; the SBOM
 * task's row says whether the run generated the SBOM or kept one whose dependencies were the
 * same. Pages pair it with ProjectDeployStores.hasDetail as the JobRunProgress expandable
 * predicate.
 */
const props = defineProps<{
  /** The organization the run deployed for, whose log store holds the workloads' logs. */
  organizationId: string | null
  node: JobTaskNode
  root: JobTaskNode | null
}>()

const artifacts = computed(() => ProjectDeployStores.artifactsOf(props.node))
const sbom = computed(() => ProjectDeployStores.sbomOf(props.node))
// an SBOM recorded after the task started is the one its workload generated; an older one was kept
const sbomGenerated = computed(() => sbom.value !== null && props.node.started !== null && sbom.value.generated >= props.node.started)
const workloadId = computed(() => ProjectDeployStores.workloadLogOf(props.node, props.root))
// The workload ran for this task, so the task's own span is the window its log falls in
const run = computed<WorkloadRun>(() => ({ started: props.node.started, finished: props.node.finished }))
</script>
