<template>
  <JobRunProgress :key="jobRunId" :job-run-id="jobRunId"
                  :failure-of="systemRun ? undefined : ProjectDeployResultNames.failureOf"
                  :next-step="systemRun ? SYSTEM_JOB_RETRY_HINT : undefined" @loaded="onLoaded" />
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { JobRun } from '@kinotic-ai/management-api'
import { JobRunProgress, ProjectDeployResultNames } from '@kinotic-ai/frontend-common/management'

/**
 * One job run's progress, step by step, with each step's detail. A failed deployment explains its
 * failure as a deployment, and a failed system run says how to run it again. Without the
 * organization the run belongs to, it emits that organization once the run is loaded.
 */
const props = defineProps<{
  jobRunId: string
  organizationId?: string
}>()

const emit = defineEmits<{
  (e: 'organization', organizationId: string | null): void
}>()

const SYSTEM_JOB_RETRY_HINT = 'To try again, start the job again with Run system job on the Jobs page.'

// true once the loaded run is one of the system jobs' runs, which no organization owns
const systemRun = ref(false)

// another run's organization is cleared at once, so a run that fails to load never shows the last one's
watch(() => props.jobRunId, () => {
  systemRun.value = false
  if (!props.organizationId) {
    emit('organization', null)
  }
})

function onLoaded(run: JobRun): void {
  systemRun.value = !run.organizationId
  if (!props.organizationId) {
    emit('organization', run.organizationId)
  }
}
</script>
