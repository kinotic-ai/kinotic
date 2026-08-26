<template>
  <div class="flex flex-col gap-4">
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <template v-if="run">
      <div class="flex flex-col gap-1">
        <div class="flex flex-wrap items-center gap-3">
          <h2 class="text-xl font-semibold">{{ run.name }}</h2>
          <Tag :value="run.status" :severity="runStatusSeverity(run.status)" />
          <span v-if="live" class="flex items-center gap-1.5 text-xs text-sky-400">
            <span class="h-2 w-2 animate-pulse rounded-full bg-sky-400" />
            live
          </span>
        </div>
        <p v-if="run.description" class="text-sm text-muted-color">{{ run.description }}</p>
        <div class="flex flex-wrap items-center gap-4 text-xs text-muted-color">
          <span v-if="run.started">Started {{ formatEpochDateTime(run.started) }}</span>
          <span v-if="run.started">Duration {{ formatDuration(run.started, run.finished, now) }}</span>
          <span v-if="run.resumedFrom">Resumed from <span class="font-mono">{{ run.resumedFrom }}</span></span>
        </div>
      </div>

      <Message v-if="run.error" severity="error" :closable="false">{{ run.error }}</Message>

      <JobStepPipeline v-if="root && root.children.length > 0" :steps="root.children" />

      <ProgressBar v-if="live && root?.progress"
                   :value="root.progress.percentageComplete"
                   :show-value="false"
                   class="!h-2" />

      <JobTaskTree v-if="root" :root="root" :now="now" />
    </template>

    <div v-else-if="loading" class="p-6 text-sm text-muted-color">Loading job run…</div>
  </div>
</template>

<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import Message from 'primevue/message'
import ProgressBar from 'primevue/progressbar'
import Tag from 'primevue/tag'
import DatetimeUtil from '../../util/DatetimeUtil'
import JobStepPipeline from './JobStepPipeline.vue'
import JobTaskTree from './JobTaskTree.vue'
import { runStatusSeverity } from './jobRunDisplay'
import { useJobRunProgress } from './useJobRunProgress'

/**
 * The progress of one grind job run: header with status and timing, the top-level steps as
 * a pipeline, and the full step ledger as a tree. Live-updates while the run executes and
 * renders the persisted history once it is terminal.
 */
const props = defineProps<{
  jobRunId: string
}>()

const formatEpochDateTime = DatetimeUtil.formatEpochDateTime
const formatDuration = DatetimeUtil.formatDuration

const { run, root, loading, error, live } = useJobRunProgress(props.jobRunId)

// drives the elapsed-time displays of the run and its running steps
const now = ref(Date.now())
const ticker = setInterval(() => { now.value = Date.now() }, 1000)
onUnmounted(() => clearInterval(ticker))
</script>
