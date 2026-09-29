<template>
  <div class="flex flex-col gap-4">
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <template v-if="run">
      <div class="flex flex-col gap-1">
        <div class="flex flex-wrap items-center gap-3">
          <h2 class="font-mono text-sm font-medium tracking-wide text-surface-950 dark:text-surface-0">{{ run.name }}</h2>
          <Tag :value="run.status" :severity="executionStatusSeverity(run.status)" />
          <span v-if="live" class="flex items-center gap-1.5 rounded-full bg-sky-100 px-2 py-0.5 text-xs font-medium text-sky-700 dark:bg-sky-500/15 dark:text-sky-300">
            <span class="h-1.5 w-1.5 animate-pulse rounded-full bg-sky-500" />
            live
          </span>
        </div>
        <p v-if="run.description" class="text-sm text-muted-color">{{ run.description }}</p>
        <div class="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-color">
          <span v-if="run.started" class="inline-flex items-center gap-1.5 rounded-md bg-surface-100 px-2 py-1 dark:bg-surface-800">
            <CalendarClock :size="13" :stroke-width="1.75" aria-hidden="true" />{{ formatEpochDateTime(run.started) }}
          </span>
          <span v-if="run.started" class="inline-flex items-center gap-1.5 rounded-md bg-surface-100 px-2 py-1 font-mono tabular-nums dark:bg-surface-800">
            <Timer :size="13" :stroke-width="1.75" aria-hidden="true" />{{ formatDuration(run.started, run.finished, now) }}
          </span>
          <span v-if="run.resumedFrom" class="inline-flex items-center gap-1.5 rounded-md bg-surface-100 px-2 py-1 dark:bg-surface-800">
            <History :size="13" :stroke-width="1.75" aria-hidden="true" />Resumed from <span class="font-mono">{{ run.resumedFrom }}</span>
          </span>
        </div>
      </div>

      <Message v-if="run.error" severity="error" :closable="false">{{ run.error }}</Message>

      <JobTaskPipeline v-if="root && root.children.length > 0" :tasks="root.children" :task-icon="taskIcon" />

      <ProgressBar v-if="live" :value="percentComplete" :show-value="false" class="!h-2" />

      <JobTaskTree v-if="root" :root="root" :now="now" :expandable="expandable">
        <template #detail="{ node }">
          <slot name="detail" :node="node" :root="root" />
        </template>
      </JobTaskTree>
    </template>

    <div v-else-if="loading" class="p-6 text-sm text-muted-color">Loading job run…</div>
  </div>
</template>

<script setup lang="ts">
import { onUnmounted, ref, type Component } from 'vue'
import Message from 'primevue/message'
import ProgressBar from 'primevue/progressbar'
import Tag from 'primevue/tag'
import { CalendarClock, History, Timer } from '@lucide/vue'
import DatetimeUtil from '../../util/DatetimeUtil'
import JobTaskPipeline from './JobTaskPipeline.vue'
import JobTaskTree from './JobTaskTree.vue'
import { executionStatusSeverity } from './jobRunDisplay'
import { useJobRunProgress } from './useJobRunProgress'
import type { JobTaskNode } from './JobTaskNode'

/**
 * The progress of one grind job run: header with status and timing, the top-level tasks as
 * a pipeline, and the full task ledger as a tree. Live-updates while the run executes and
 * renders the persisted history once it is terminal. A page that knows what a job's tasks
 * produce can give a task row a detail pane: expandable says which rows have one, and the
 * detail slot renders it, given the node and the root of the tree; taskIcon gives the pipeline's
 * steps their icons.
 */
const props = defineProps<{
  jobRunId: string
  expandable?: (node: JobTaskNode) => boolean
  /** The icon of a top-level task's step in the pipeline, undefined for the default. */
  taskIcon?: (node: JobTaskNode) => Component | undefined
}>()

const formatEpochDateTime = DatetimeUtil.formatEpochDateTime
const formatDuration = DatetimeUtil.formatDuration

const { run, root, percentComplete, loading, error, live } = useJobRunProgress(props.jobRunId)

// drives the elapsed-time displays of the run and its running tasks
const now = ref(Date.now())
const ticker = setInterval(() => { now.value = Date.now() }, 1000)
onUnmounted(() => clearInterval(ticker))
</script>
