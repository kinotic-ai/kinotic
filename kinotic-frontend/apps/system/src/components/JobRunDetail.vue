<template>
  <JobRunProgress :key="jobRunId" :job-run-id="jobRunId" :failure-of="ProjectDeployResultNames.failureOf" />
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Kinotic } from '@kinotic-ai/core'
import { JobRunProgress, ProjectDeployResultNames, createDebug } from '@kinotic-ai/frontend-common'

const debug = createDebug('job-run-detail')

/**
 * One job run's progress, step by step, with each step's detail. Without the organization the run
 * belongs to, it looks the run up and emits that organization once known.
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
