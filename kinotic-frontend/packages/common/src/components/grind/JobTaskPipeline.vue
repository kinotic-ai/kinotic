<template>
  <div class="flex items-start overflow-x-auto py-4">
    <template v-for="(task, index) in tasks" :key="task.taskPath">
      <div v-if="index > 0"
           class="mt-[1.125rem] h-0.5 min-w-8 flex-1 rounded"
           :class="connectorClass(tasks[index - 1])" />
      <div class="flex w-28 shrink-0 flex-col items-center gap-2 px-1">
        <div class="relative flex h-9 w-9 items-center justify-center rounded-full border-2"
             :class="nodeClass(task)">
          <span v-if="task.status === ExecutionStatus.RUNNING"
                class="absolute inset-0 animate-ping rounded-full border-2 border-sky-400 opacity-60" />
          <i :class="nodeIcon(task)" class="text-sm" />
        </div>
        <span class="w-full truncate text-center text-xs text-muted-color"
              :title="task.description">{{ task.description || `Task ${task.sequence}` }}</span>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ExecutionStatus } from '@kinotic-ai/management-api'
import type { JobTaskNode } from './JobTaskNode'

/**
 * The run's top-level tasks as a horizontal pipeline: one glowing node per task, joined by
 * connectors that take on the completed or failed color of the task they leave.
 */
defineProps<{
  tasks: JobTaskNode[]
}>()

function nodeClass(task: JobTaskNode): string {
  let ret: string
  if (task.status === ExecutionStatus.COMPLETED) {
    ret = 'border-emerald-500 text-emerald-400 shadow-[0_0_12px_rgba(16,185,129,0.45)]'
  } else if (task.status === ExecutionStatus.RUNNING) {
    ret = 'border-sky-400 text-sky-400 shadow-[0_0_12px_rgba(56,189,248,0.45)]'
  } else if (task.status === ExecutionStatus.FAILED) {
    ret = 'border-red-500 text-red-400 shadow-[0_0_14px_rgba(239,68,68,0.55)]'
  } else if (task.status === ExecutionStatus.CANCELLED) {
    ret = 'border-amber-500 text-amber-400'
  } else {
    ret = 'border-surface text-muted-color'
  }
  return ret
}

function nodeIcon(task: JobTaskNode): string {
  let ret: string
  if (task.status === ExecutionStatus.COMPLETED) {
    ret = 'pi pi-check'
  } else if (task.status === ExecutionStatus.RUNNING) {
    ret = 'pi pi-spin pi-spinner'
  } else if (task.status === ExecutionStatus.FAILED) {
    ret = 'pi pi-times'
  } else if (task.status === ExecutionStatus.CANCELLED) {
    ret = 'pi pi-ban'
  } else {
    ret = 'pi pi-clock'
  }
  return ret
}

function connectorClass(previous: JobTaskNode): string {
  let ret: string
  if (previous.status === ExecutionStatus.COMPLETED) {
    ret = 'bg-emerald-500/60'
  } else if (previous.status === ExecutionStatus.FAILED) {
    ret = 'bg-red-500/60'
  } else if (previous.status === ExecutionStatus.RUNNING) {
    ret = 'bg-sky-400/40'
  } else {
    ret = 'bg-[var(--p-content-border-color)]'
  }
  return ret
}
</script>
