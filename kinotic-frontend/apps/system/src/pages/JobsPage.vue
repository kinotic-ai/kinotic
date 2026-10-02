<template>
  <div class="flex flex-col">
    <PageHeader title="Jobs" :description="description">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined @click="refresh" />
      </template>
    </PageHeader>

    <!-- how the runs have gone, at a glance: the share that completed and the split by outcome -->
    <section v-if="counted.length > 0"
             class="mb-4 flex flex-wrap items-center gap-x-8 gap-y-3 rounded-xl border border-surface-200 bg-surface-0 px-5 py-4 dark:border-surface-700 dark:bg-surface-800/30">
      <div class="flex items-center gap-3">
        <span :class="['flex h-10 w-10 shrink-0 items-center justify-center rounded-lg', scopeTint(scope)]">
          <LaptopMinimalCheck :size="18" :stroke-width="1.75" aria-hidden="true" />
        </span>
        <div class="leading-tight">
          <div class="text-[0.6875rem] font-semibold uppercase tracking-wider text-muted-color">Success rate</div>
          <div class="mt-0.5 text-sm"><b class="text-lg font-semibold tabular-nums text-surface-950 dark:text-surface-0">{{ successRate }}%</b>
            <span class="text-muted-color"> of {{ finished }} finished {{ finished === 1 ? 'run' : 'runs' }}</span></div>
        </div>
      </div>
      <div class="min-w-[14rem] flex-1">
        <div class="flex h-2 overflow-hidden rounded-full bg-surface-100 dark:bg-surface-800" aria-hidden="true">
          <span v-for="part in outcome" :key="part.label" :class="['h-full', part.bar]" :style="{ width: `${part.share}%` }" />
        </div>
        <div class="mt-2 flex flex-wrap justify-end gap-x-4 gap-y-1 text-xs text-muted-color">
          <span v-for="part in outcome" :key="part.label" class="flex items-center gap-1.5">
            <span :class="['h-2 w-2 rounded-full', part.bar]" aria-hidden="true" />
            {{ part.label }} <b class="font-semibold tabular-nums text-surface-800 dark:text-surface-100">{{ part.count }}</b>
          </span>
        </div>
      </div>
    </section>

    <StatusChips v-model="statusFilter" :chips="chips" class="mb-4" />

    <JobRunsTable ref="jobRunsTable"
                  :organization-id="scope.organizationId"
                  :application-id="scope.applicationId"
                  :project-id="scope.projectId"
                  :status="statusFilter"
                  @open="openRun" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import { LaptopMinimalCheck } from '@lucide/vue'

import { ExecutionStatus, type JobRun } from '@kinotic-ai/management-api'
import { JobRunsTable, PageHeader, executionStatusSeverity, scanJobRuns } from '@kinotic-ai/frontend-common'

import StatusChips, { type StatusChip } from '@/components/StatusChips.vue'
import { scopeName, scopePath, scopeTint, type Scope } from '@/util/scope'

/**
 * The job runs of the scope the route names: every run on the platform, or the deployments of
 * an organization, an application or a project. State chips carry the counts
 * and live in the URL so a tile can link to the failed runs.
 */
const props = defineProps<{
  organizationId?: string
  applicationId?: string
  projectId?: string
}>()

const CHIP_STATES = [ExecutionStatus.RUNNING, ExecutionStatus.COMPLETED, ExecutionStatus.FAILED]

const route = useRoute()
const router = useRouter()
const jobRunsTable = ref<InstanceType<typeof JobRunsTable>>()

const scope = computed<Scope>(() => ({
  organizationId: props.organizationId,
  applicationId: props.applicationId,
  projectId: props.projectId
}))

const description = computed(() => scope.value.organizationId
    ? `Job runs executed for ${scopeName(scope.value)}: its deployments.`
    : 'Grind job runs across the platform: deployments, step by step.')

const counted = ref<JobRun[]>([])

/** The runs by outcome, in the status colours the tags use, each with its share of all the runs. */
const outcome = computed(() => {
  const total = counted.value.length || 1
  return [
    { label: 'Completed', status: ExecutionStatus.COMPLETED, bar: 'bg-green-500' },
    { label: 'Failed', status: ExecutionStatus.FAILED, bar: 'bg-red-500' },
    { label: 'Running', status: ExecutionStatus.RUNNING, bar: 'bg-sky-500' }
  ].map(part => {
    const count = counted.value.filter(run => run.status === part.status).length
    return { ...part, count, share: Math.round((count / total) * 100) }
  })
})

const finished = computed(() => counted.value.filter(run =>
    run.status === ExecutionStatus.COMPLETED || run.status === ExecutionStatus.FAILED).length)

const successRate = computed(() => finished.value === 0
    ? 0
    : Math.round((counted.value.filter(run => run.status === ExecutionStatus.COMPLETED).length / finished.value) * 100))

const statusFilter = computed<ExecutionStatus | null>({
  get: () => CHIP_STATES.includes(route.query.status as ExecutionStatus) ? route.query.status as ExecutionStatus : null,
  set: value => { router.replace({ query: { ...route.query, status: value ?? undefined } }) }
})

const chips = computed<StatusChip[]>(() => [
  { label: 'All', value: null, count: counted.value.length },
  ...CHIP_STATES.map(state => ({
    label: state.charAt(0) + state.slice(1).toLowerCase(),
    value: state,
    count: counted.value.filter(run => run.status === state).length,
    severity: executionStatusSeverity(state)
  }))
])

// The chips count from a scan of the scope's runs; the table pages the runs itself
async function count() {
  try {
    counted.value = await scanJobRuns({
      organizationId: scope.value.organizationId,
      applicationId: scope.value.applicationId,
      projectId: scope.value.projectId
    })
  } catch {
    // The chips keep their last counts; the table reports the failure itself
  }
}

function refresh() {
  jobRunsTable.value?.refresh()
  count()
}

function openRun(jobRunId: string) {
  router.push(`${scopePath(scope.value)}/jobs/${encodeURIComponent(jobRunId)}`)
}

// The header's switchers navigate in place, so the router reuses this instance across scopes
watch(() => [scope.value.organizationId, scope.value.applicationId, scope.value.projectId], count, { immediate: true })
</script>
