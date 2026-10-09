<template>
  <JobRunProgress :key="jobRunId" :job-run-id="jobRunId" :expandable="hasLogs" :failure-of="failureOf"
                  next-step="To try again, run the job again from System jobs.">
    <template #detail="{ node }">
      <TaskLogView :entries="node.logs" />
    </template>
  </JobRunProgress>
</template>

<script setup lang="ts">
import { JobRunProgress, type JobTaskFailure, type JobTaskNode } from '@kinotic-ai/frontend-common'

import TaskLogView from '@/components/TaskLogView.vue'

/**
 * One system job run's progress, task by task at any depth, including the tasks the run
 * generated as it went; a task that wrote to its log opens to show it.
 */
defineProps<{
  jobRunId: string
}>()

function hasLogs(node: JobTaskNode): boolean {
  return node.logs.length > 0
}

function failureOf(node: JobTaskNode): JobTaskFailure {
  return {
    explanation: hasLogs(node) ? 'Open the step\'s log to see what went wrong.' : (node.error ?? 'The step failed.'),
    platform: false
  }
}
</script>
