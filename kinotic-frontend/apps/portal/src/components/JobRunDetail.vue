<template>
  <JobRunProgress :key="jobRunId" :job-run-id="jobRunId" :expandable="ProjectDeployResultNames.hasDetail" :task-icon="ProjectDeployResultNames.iconOf" :failure-of="ProjectDeployResultNames.failureOf"
                  :next-step="projectDeploymentPath ? ProjectDeployResultNames.RETRY_HINT : undefined">
    <template #detail="{ node, root }">
      <ProjectDeployTaskDetail :organization-id="organizationId" :node="node" :root="root" />
    </template>
  </JobRunProgress>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Kinotic } from '@kinotic-ai/core'
import { createDebug, JobRunProgress, ProjectDeployResultNames, ProjectDeployTaskDetail } from '@kinotic-ai/frontend-common'
import { KinoticStates } from '@/states'

const debug = createDebug('job-run-detail')

/**
 * One job run's progress, step by step, with each step's detail. Emits project with the path of
 * the run's project Deployment page once it is known, null for a run outside a project.
 */
const props = defineProps<{
  jobRunId: string
}>()

const emit = defineEmits<{
  (e: 'project', deploymentPath: string | null): void
}>()

const organizationId = KinoticStates.getUserState().getOrganizationId()

const projectDeploymentPath = ref<string | null>(null)

watch(() => props.jobRunId, loadOwningProject, { immediate: true })

async function loadOwningProject(): Promise<void> {
  projectDeploymentPath.value = null
  try {
    const run = await Kinotic.jobMonitoring.findJobRun(props.jobRunId)
    if (run.applicationId && run.projectId) {
      projectDeploymentPath.value =
          `/application/${encodeURIComponent(run.applicationId)}/project/${encodeURIComponent(run.projectId)}/deployment`
    }
  } catch (error) {
    debug('Failed to load job run %s: %O', props.jobRunId, error)
  }
  emit('project', projectDeploymentPath.value)
}
</script>
