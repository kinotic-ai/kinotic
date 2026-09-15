<template>
  <template v-if="ProjectDeployStores.hasArtifacts(node)">
    <ProjectArtifactsDetail v-if="artifacts" :artifacts="artifacts" />
    <span v-else class="text-xs text-muted-color">Waiting for the sync workload's artifact report</span>
  </template>
  <template v-else>
    <WorkloadLogView v-if="workloadId" :key="workloadId" :workload-id="workloadId" />
    <span v-else class="text-xs text-muted-color">Waiting for the deployment target</span>
  </template>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { JobTaskNode } from '../grind/JobTaskNode'
import WorkloadLogView from '../WorkloadLogView.vue'
import ProjectArtifactsDetail from './ProjectArtifactsDetail.vue'
import ProjectDeployStores from './ProjectDeployStores'

/**
 * The detail pane of one task row of a project deployment run: the artifacts the run bound,
 * or the log of the workload the task ran, with a placeholder until either is known. Pages
 * pair it with ProjectDeployStores.hasDetail as the JobRunProgress expandable predicate.
 */
const props = defineProps<{
  node: JobTaskNode
  root: JobTaskNode | null
}>()

const artifacts = computed(() => ProjectDeployStores.artifactsOf(props.node))
const workloadId = computed(() => ProjectDeployStores.workloadLogOf(props.node, props.root))
</script>
