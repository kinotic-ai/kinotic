<template>
  <JobRunProgress :key="jobRunId" :job-run-id="jobRunId" :expandable="ProjectDeployResultNames.hasDetail" :task-icon="ProjectDeployResultNames.iconOf" :failure-of="ProjectDeployResultNames.failureOf">
    <template #detail="{ node, root }">
      <ProjectDeployTaskDetail :organization-id="organizationId ?? owningOrganizationId" :node="node" :root="root" />
    </template>
  </JobRunProgress>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Kinotic } from '@kinotic-ai/core'
import { JobRunProgress, ProjectDeployResultNames, ProjectDeployTaskDetail, createDebug } from '@kinotic-ai/frontend-common'

const debug = createDebug('job-run-detail')

/**
 * One job run's progress, step by step, with each step's detail, read in the given organization;
 * without one, in the organization the run belongs to, which it emits as organization once known.
 */
const props = defineProps<{
  jobRunId: string
  organizationId?: string
}>()

const emit = defineEmits<{
  (e: 'organization', organizationId: string | null): void
}>()

const owningOrganizationId = ref<string | null>(null)

watch(() => props.jobRunId, loadOwningOrganization, { immediate: true })

async function loadOwningOrganization(): Promise<void> {
  owningOrganizationId.value = null
  if (props.organizationId) {
    return
  }
  try {
    const run = await Kinotic.jobMonitoring.findJobRun(props.jobRunId)
    owningOrganizationId.value = run.organizationId
  } catch (error) {
    debug('Failed to load job run %s: %O', props.jobRunId, error)
  }
  emit('organization', owningOrganizationId.value)
}
</script>
