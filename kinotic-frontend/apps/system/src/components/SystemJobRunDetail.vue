<template>
  <JobRunProgress :key="jobRunId" :job-run-id="jobRunId" next-step="To try again, run the job again from System jobs." />
</template>

<script setup lang="ts">
import { JobRunProgress } from '@kinotic-ai/frontend-common'

/**
 * One system job run's progress, task by task at any depth, including the tasks the run generated
 * as it went, each with the icon and the view it declares.
 */
defineProps<{
  jobRunId: string
}>()
</script>
