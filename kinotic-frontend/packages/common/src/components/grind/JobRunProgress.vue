<template>
  <div class="flex flex-col gap-4">
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <template v-if="run">
      <section class="overflow-hidden rounded-xl border border-surface-200 bg-surface-0 dark:border-surface-800 dark:bg-surface-900">
        <div class="flex items-start gap-4 p-5">
          <span :class="['flex h-11 w-11 shrink-0 items-center justify-center rounded-xl', TASK_STATUS_STYLE[run.status].tile]">
            <component :is="TASK_STATUS_STYLE[run.status].rowIcon" :size="22" :stroke-width="1.75"
                       :class="TASK_STATUS_STYLE[run.status].row" aria-hidden="true" />
          </span>
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-2.5">
              <h2 class="min-w-0 truncate font-mono text-base font-semibold text-surface-950 dark:text-surface-0">{{ run.name }}</h2>
              <Tag :value="run.status" :severity="executionStatusSeverity(run.status)" />
              <span v-if="live" class="flex items-center gap-1.5 rounded-full bg-sky-100 px-2 py-0.5 text-xs font-medium text-sky-700 dark:bg-sky-500/15 dark:text-sky-300">
                <span class="h-1.5 w-1.5 animate-pulse rounded-full bg-sky-500" />
                live
              </span>
            </div>
            <p v-if="run.description" class="mt-1 break-words text-sm text-muted-color">{{ run.description }}</p>
          </div>
        </div>

        <dl class="grid grid-cols-2 gap-px border-t border-surface-200 bg-surface-200 sm:auto-cols-fr sm:grid-flow-col sm:grid-cols-none dark:border-surface-800 dark:bg-surface-800">
          <div v-for="fact in facts" :key="fact.label" class="bg-surface-0 px-5 py-3.5 dark:bg-surface-900">
            <dt class="flex items-center gap-1.5 text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-color">
              <component :is="fact.icon" :size="13" :stroke-width="1.75" aria-hidden="true" />{{ fact.label }}
            </dt>
            <dd :class="['m-0 mt-1 truncate text-sm font-medium text-surface-900 dark:text-surface-50', fact.mono ? 'font-mono tabular-nums' : '']">
              {{ fact.value }}
            </dd>
          </div>
        </dl>

        <div v-if="run.status === ExecutionStatus.FAILED" class="border-t border-red-100 bg-red-50/60 px-5 py-4 dark:border-red-500/25 dark:bg-red-500/[0.07]">
          <div class="flex items-start gap-3">
            <CircleAlert :size="18" :stroke-width="1.75" class="mt-0.5 shrink-0 text-red-600 dark:text-red-400" aria-hidden="true" />
            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <p class="text-sm font-semibold text-red-800 dark:text-red-300">
                  {{ failedStep ? `${failedStep.description || `Step ${failedStep.sequence}`} failed` : 'The run failed' }}
                </p>
                <span v-if="failure?.platform"
                      class="inline-flex items-center gap-1 rounded-md bg-surface-0 px-1.5 py-0.5 text-[0.6875rem] font-medium text-surface-700 ring-1 ring-surface-200 dark:bg-surface-900 dark:text-surface-200 dark:ring-surface-700">
                  <ShieldAlert :size="12" :stroke-width="2" aria-hidden="true" />Kinotic platform error
                </span>
              </div>
              <p v-if="failure" class="mt-1 text-sm text-surface-700 dark:text-surface-200">{{ failure.explanation }}</p>
              <p v-if="completedSteps > 0" class="mt-1 text-sm text-surface-700 dark:text-surface-200">
                {{ completedSteps === 1 ? 'The step before it completed' : `The ${completedSteps} steps before it completed` }},
                and what {{ completedSteps === 1 ? 'it' : 'they' }} did is unaffected.
              </p>
              <p v-if="nextStep" class="mt-1 text-sm text-surface-700 dark:text-surface-200">{{ nextStep }}</p>

              <div class="mt-3 flex flex-wrap items-center gap-2">
                <Button v-if="failedStep" size="small" :label="expandable?.(failedStep) ? 'View step log' : 'Show step'"
                        icon="pi pi-arrow-down" @click="revealFailedStep" />
                <Button v-if="run.id" size="small" severity="secondary" outlined
                        :label="copied ? 'Copied' : 'Copy reference ID'" :icon="copied ? 'pi pi-check' : 'pi pi-copy'"
                        @click="copyReference(run.id)" />
                <Button size="small" severity="secondary" text :label="detailsOpen ? 'Hide technical details' : 'View technical details'"
                        :icon="detailsOpen ? 'pi pi-chevron-up' : 'pi pi-chevron-down'" icon-pos="right"
                        :aria-expanded="detailsOpen" @click="detailsOpen = !detailsOpen" />
              </div>

              <dl v-if="detailsOpen"
                  class="mt-3 grid gap-x-4 gap-y-2 rounded-lg border border-red-100 bg-surface-0 p-3 text-[0.8125rem] sm:grid-cols-[auto_1fr] dark:border-red-500/20 dark:bg-surface-900">
                <template v-for="detail in technicalDetails" :key="detail.label">
                  <dt class="text-muted-color">{{ detail.label }}</dt>
                  <dd class="m-0 min-w-0 whitespace-pre-wrap break-words font-mono text-surface-800 dark:text-surface-100">{{ detail.value }}</dd>
                </template>
              </dl>
            </div>
          </div>
        </div>
      </section>

      <JobTaskPipeline v-if="root && root.children.length > 0" :tasks="root.children" :task-icon="taskIcon"
                       @select="task => tree?.reveal(task.taskPath)" />

      <ProgressBar v-if="live" :value="percentComplete" :show-value="false" class="!h-2" />

      <JobTaskTree v-if="root" ref="tree" :root="root" :now="now" :expandable="expandable">
        <template #detail="{ node }">
          <slot name="detail" :node="node" :root="root" />
        </template>
      </JobTaskTree>
    </template>

    <div v-else-if="loading" class="p-6 text-sm text-muted-color">Loading job run…</div>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onUnmounted, ref, type Component } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import ProgressBar from 'primevue/progressbar'
import Tag from 'primevue/tag'
import { CalendarCheck, CalendarClock, CircleAlert, History, ShieldAlert, Timer } from '@lucide/vue'
import { ExecutionStatus } from '@kinotic-ai/management-api'
import DatetimeUtil from '../../util/DatetimeUtil'
import JobTaskPipeline from './JobTaskPipeline.vue'
import JobTaskTree from './JobTaskTree.vue'
import { executionStatusSeverity, TASK_STATUS_STYLE } from './jobRunDisplay'
import { useJobRunProgress } from './useJobRunProgress'
import type { JobTaskFailure } from './JobTaskFailure'
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
  /** How a failed top-level task reads to the user; without it a failure shows only its raw error. */
  failureOf?: (node: JobTaskNode) => JobTaskFailure
  /** What the user can do about a failed run, such as how to start another. */
  nextStep?: string
}>()

const formatEpochDateTime = DatetimeUtil.formatEpochDateTime
const formatDuration = DatetimeUtil.formatDuration

const { run, root, percentComplete, loading, error, live } = useJobRunProgress(props.jobRunId)

const tree = ref<InstanceType<typeof JobTaskTree> | null>(null)
const detailsOpen = ref(false)
const copied = ref(false)

/** The pipeline step that failed, the first one when several did. */
const failedStep = computed<JobTaskNode | null>(() =>
    root.value?.children.find(child => child.status === ExecutionStatus.FAILED) ?? null)

const failure = computed<JobTaskFailure | null>(() =>
    failedStep.value && props.failureOf ? props.failureOf(failedStep.value) : null)

const completedSteps = computed<number>(() =>
    root.value?.children.filter(child => child.status === ExecutionStatus.COMPLETED).length ?? 0)

// Everything support needs to find the failure, shown only on request
const technicalDetails = computed<{ label: string, value: string }[]>(() => {
  const current = run.value
  const out: { label: string, value: string }[] = []
  if (current?.id) {
    out.push({ label: 'Reference ID', value: current.id })
  }
  if (failedStep.value) {
    out.push({ label: 'Step', value: `${failedStep.value.description} (${failedStep.value.taskPath})` })
  }
  const error = current?.error ?? failedStep.value?.error
  if (error) {
    out.push({ label: 'Error', value: error })
  }
  if (current?.nodeId) {
    out.push({ label: 'Node', value: current.nodeId })
  }
  return out
})

function revealFailedStep(): void {
  if (failedStep.value) {
    tree.value?.reveal(failedStep.value.taskPath)
  }
}

async function copyReference(id: string): Promise<void> {
  await navigator.clipboard.writeText(id)
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}

interface RunFact {
  label: string
  icon: Component
  value: string
  mono?: boolean
}

// The run's timing, one labelled cell each; Resumed from only for a run that resumed another
const facts = computed<RunFact[]>(() => {
  const current = run.value
  const out: RunFact[] = []
  if (current) {
    out.push(
      { label: 'Started', icon: markRaw(CalendarClock), value: current.started ? formatEpochDateTime(current.started) : 'Not started' },
      { label: 'Finished', icon: markRaw(CalendarCheck), value: current.finished ? formatEpochDateTime(current.finished) : (current.started ? 'In progress' : '—') },
      { label: 'Duration', icon: markRaw(Timer), value: current.started ? formatDuration(current.started, current.finished, now.value) : '—', mono: true }
    )
    if (current.resumedFrom) {
      out.push({ label: 'Resumed from', icon: markRaw(History), value: current.resumedFrom, mono: true })
    }
  }
  return out
})

// drives the elapsed-time displays of the run and its running tasks
const now = ref(Date.now())
const ticker = setInterval(() => { now.value = Date.now() }, 1000)
onUnmounted(() => clearInterval(ticker))
</script>
