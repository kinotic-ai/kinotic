<template>
  <JobRunProgress :key="jobRunId" :job-run-id="jobRunId" :expandable="hasWorkloadLog" :failure-of="failureOf"
                  next-step="To try again, run the job again from System jobs.">
    <template #detail="{ node }">
      <WorkloadLogView v-if="workloadOf(node)" :key="workloadOf(node)!" :organization-id="null"
                       :workload-id="workloadOf(node)!" :run="{ started: node.started, finished: node.finished }" />
    </template>
  </JobRunProgress>
</template>

<script setup lang="ts">
import { JobRunProgress, WorkloadLogView, type JobTaskFailure, type JobTaskNode } from '@kinotic-ai/frontend-common'

/**
 * One system job run's progress, task by task at any depth, including the tasks the run generated
 * as it went. A step that runs a platform workload opens to that workload's console log: the step
 * is a nested definition whose task stored the workload's id under WORKLOAD_ID, and the step's own
 * span is the window the log falls in.
 */
defineProps<{
  jobRunId: string
}>()

/** Mirrors SystemJob.WORKLOAD_ID on the server. */
const WORKLOAD_ID = 'workloadId'

/** The id of the workload the step runs, or null for a step that runs none or has not named it yet. */
function workloadOf(node: JobTaskNode): string | null {
  const allocated = node.children.find(child => child.storedName === WORKLOAD_ID)?.storedValue
  return typeof allocated === 'string' ? allocated : null
}

function hasWorkloadLog(node: JobTaskNode): boolean {
  return workloadOf(node) !== null
}

function failureOf(node: JobTaskNode): JobTaskFailure {
  return {
    explanation: hasWorkloadLog(node) ? 'Open the step\'s log to see what went wrong.' : (node.error ?? 'The step failed.'),
    platform: false
  }
}
</script>
