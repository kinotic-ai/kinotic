<template>
  <div class="flex flex-col">
    <PageHeader title="Deployment">
      <template #actions>
        <Button label="All runs" icon="pi pi-list-check" severity="secondary" outlined @click="router.push(`${basePath}/jobs`)" />
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <EmptyChartCharacter v-if="!loading && !latestRun" class="rounded-xl border border-dashed border-surface-300 py-14 dark:border-surface-700"
                         title="Never deployed" hint="Pushing to the repository's default branch deploys this project." />

    <template v-if="latestRun">
      <div class="mb-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard :icon="CloudUpload" :tint="TINTS.purple" label="Status"
                  :detail="latestRun.started ? `Started ${DatetimeUtil.formatRelativeDate(latestRun.started)}` : undefined">
          <Tag :value="latestRun.status" :severity="executionStatusSeverity(latestRun.status)" />
        </StatCard>
        <StatCard :icon="GitCommitHorizontal" :tint="TINTS.purple" label="Commit" detail="the commit the latest run deploys">
          <span class="font-mono text-2xl font-semibold tracking-tight text-surface-950 dark:text-surface-0"
                v-tooltip.top="latestSha ?? undefined">{{ latestSha ? shortSha(latestSha) : '—' }}</span>
        </StatCard>
        <StatCard :icon="Server" :tint="TINTS.purple" label="Microservices" :value="services.length"
                  :detail="`${runningServices} running, each in a VM of its own`" />
        <StatCard :icon="LaptopMinimalCheck" :tint="TINTS.purple" label="Deploy runs" :value="deployRuns.length"
                  :detail="`${failedRuns} failed`" :to="`${basePath}/jobs`" />
      </div>

      <Message v-if="latestRun.status === ExecutionStatus.FAILED && latestRun.error" severity="error" :closable="false" class="mb-4">
        {{ latestRun.error }}
      </Message>

      <div class="mb-4 grid gap-4 lg:grid-cols-3">
        <DashboardSection :icon="Activity" :tint="TINTS.purple" title="Workloads by status" :count="services.length">
          <div class="flex flex-col gap-4 p-5">
            <div v-for="bucket in statusBuckets" :key="bucket.label" :class="['border-l-[3px] pl-3', bucket.border]">
              <div class="text-2xl font-semibold leading-7 tabular-nums text-surface-950 dark:text-surface-0">{{ bucket.count }}</div>
              <div class="text-xs text-muted-color">{{ bucket.label }}</div>
            </div>
          </div>
        </DashboardSection>

        <DashboardSection :icon="Network" :tint="TINTS.purple" title="Placement" :count="placement.length"
                          description="The worker nodes the microservices run on.">
          <EmptyChartCharacter v-if="placement.length === 0" class="py-6" title="No microservice has been placed on a node" />
          <div v-else class="divide-y divide-surface-100 px-5 py-2 text-sm dark:divide-surface-800">
            <div v-for="row in placement" :key="row.nodeId" class="flex items-center gap-3 py-2.5">
              <Server :size="16" :stroke-width="1.75" class="shrink-0 text-surface-400" aria-hidden="true" />
              <RouterLink :to="`/worker-nodes/${encodeURIComponent(row.nodeId)}`"
                          class="w-28 shrink-0 truncate text-surface-800 hover:underline dark:text-surface-100" v-tooltip.top="row.name">{{ row.name }}</RouterLink>
              <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700">
                <div class="h-full rounded-full bg-sky-500" :style="{ width: `${row.percent}%` }" />
              </div>
              <span class="w-10 text-right text-xs tabular-nums text-muted-color">{{ row.percent }}%</span>
              <span class="w-6 text-right text-xs font-medium tabular-nums text-surface-800 dark:text-surface-100">{{ row.count }}</span>
            </div>
          </div>
        </DashboardSection>

        <DashboardSection :icon="History" :tint="TINTS.purple" title="Deploy runs" :count="deployRuns.length"
                          :link-to="`${basePath}/jobs`">
          <div class="flex flex-col gap-4 p-5">
            <div v-for="row in runSummary" :key="row.label" class="flex items-center gap-3">
              <span :class="['flex h-9 w-9 shrink-0 items-center justify-center rounded-lg', row.tint]">
                <component :is="row.icon" :size="18" :stroke-width="1.75" aria-hidden="true" />
              </span>
              <div>
                <div class="text-2xl font-semibold leading-7 tabular-nums text-surface-950 dark:text-surface-0">{{ row.count }}</div>
                <div class="text-xs text-muted-color">{{ row.label }}</div>
              </div>
            </div>
          </div>
        </DashboardSection>
      </div>

      <div class="flex flex-col gap-4">
        <DashboardSection :icon="LaptopMinimalCheck" :tint="TINTS.purple" title="Latest deployment run"
                          description="Each step of the latest deploy run, live while it runs; open a step for its detail.">
          <div class="p-5">
            <JobRunProgress :key="latestRun.id ?? ''" :job-run-id="latestRun.id ?? ''" :expandable="ProjectDeployResultNames.hasDetail" :task-icon="ProjectDeployResultNames.iconOf" :failure-of="ProjectDeployResultNames.failureOf">
              <template #detail="{ node, root }">
                <ProjectDeployTaskDetail :organization-id="organizationId" :node="node" :root="root" />
              </template>
            </JobRunProgress>
          </div>
        </DashboardSection>

        <DashboardSection :icon="Server" :tint="TINTS.purple" title="Runtime workloads" :count="services.length"
                          description="The microservice VMs this project's deployments have left running, where the platform placed them, and what an operator can do about each.">
          <!-- WorkloadsTable brings its own search bar and paginator, which need the card's padding -->
          <div v-if="services.length > 0" class="p-4">
            <WorkloadsTable :workloads="services" :scope="scope" :node-names="nodeNames" @changed="load" />
          </div>
          <EmptyChartCharacter v-else class="py-6" title="No microservice workload is running" />
        </DashboardSection>

        <DashboardSection :icon="Clock" :tint="TINTS.purple" title="Previous runs" :count="previousRuns.length"
                          description="Earlier deployments of this project. Open one to see its tasks.">
          <DataTable v-if="previousRuns.length > 0" :value="previousRuns" size="small" class="text-sm" row-hover
                     @row-click="openRun($event.data)">
            <Column header="Status" style="width: 10rem">
              <template #body="{ data }"><Tag :value="data.status" :severity="executionStatusSeverity(data.status)" /></template>
            </Column>
            <Column header="Commit">
              <template #body="{ data }"><span class="font-mono text-xs">{{ shaOf(data) }}</span></template>
            </Column>
            <Column header="Started" class="hidden md:table-cell">
              <template #body="{ data }"><TimePill :date="data.started" /></template>
            </Column>
            <Column header="Duration" style="width: 8rem">
              <template #body="{ data }"><span class="font-mono text-xs tabular-nums">{{ formatDuration(data.started, data.finished) }}</span></template>
            </Column>
          </DataTable>
          <EmptyChartCharacter v-else class="py-6" title="No previous runs" />
        </DashboardSection>
      </div>
    </template>

    <div v-else-if="loading" class="p-6 text-sm text-muted-color">Loading deployment…</div>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { Activity, Ban, CircleCheck, CircleX, Clock, CloudUpload, GitCommitHorizontal, History, LaptopMinimalCheck, LoaderCircle, Network, Server } from '@lucide/vue'

import { ExecutionStatus, type JobRun, type Workload } from '@kinotic-ai/management-api'
import type { VmNode } from '@kinotic-ai/system-api'
import { DashboardSection, DatetimeUtil, JobRunProgress, PageHeader, ProjectDeployResultNames, ProjectDeployTaskDetail,
         StatCard, TimePill, TINTS, errorMessage, executionStatusSeverity, scanJobRuns, shortSha, EmptyChartCharacter } from '@kinotic-ai/frontend-common'

import WorkloadsTable from '@/components/WorkloadsTable.vue'
import { loadNodes } from '@/util/nodes'
import { commitShaOf, isDeployRun } from '@/util/runs'
import { projectPath, type Scope } from '@/util/scope'
import { scanWorkloads, workloadSeverity } from '@/util/workloads'

/**
 * The project's deployment as the runtime records it: the latest deploy run with its tasks
 * rendered live, the microservice workloads the deployments left running with their node and
 * the orchestration actions, and the runs before it.
 */
const props = defineProps<{
  organizationId: string
  applicationId: string
  projectId: string
}>()

/** The status groups a workload falls in, worst last, with the colour of each. */
const STATUS_BUCKETS = [
  { severity: 'success', label: 'Running', border: 'border-green-500' },
  { severity: 'info', label: 'Starting or pending', border: 'border-sky-500' },
  { severity: 'warn', label: 'Stopping', border: 'border-amber-500' },
  { severity: 'danger', label: 'Failed', border: 'border-red-500' },
  { severity: 'secondary', label: 'Stopped', border: 'border-surface-300 dark:border-surface-600' }
]

const router = useRouter()
const formatDuration = DatetimeUtil.formatDuration

const scope = computed<Scope>(() => ({
  organizationId: props.organizationId,
  applicationId: props.applicationId,
  projectId: props.projectId
}))
const basePath = computed(() => projectPath(props.organizationId, props.applicationId, props.projectId))

const deployRuns = ref<JobRun[]>([])
const workloads = ref<Workload[]>([])
const nodes = ref<VmNode[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const latestRun = computed(() => deployRuns.value[0] ?? null)
const latestSha = computed(() => latestRun.value ? commitShaOf(latestRun.value) : null)
const previousRuns = computed(() => deployRuns.value.slice(1))
const services = computed(() => workloads.value.filter(workload => workload.detached))
const nodeNames = computed(() => Object.fromEntries(nodes.value.map(node => [node.id, node.name])))
const runningServices = computed(() => services.value.filter(workload => workloadSeverity(workload.status) === 'success').length)
const failedRuns = computed(() => deployRuns.value.filter(run => run.status === ExecutionStatus.FAILED).length)

/** How many microservice workloads are in each status group; groups past Failed show only when used. */
const statusBuckets = computed(() => {
  const severities = services.value.map(workload => workloadSeverity(workload.status))
  return STATUS_BUCKETS
      .map(bucket => ({ ...bucket, count: severities.filter(severity => severity === bucket.severity).length }))
      .filter(bucket => bucket.count > 0 || bucket.severity !== 'secondary')
})

/** How many microservice workloads each node holds, most first, with their share of the placed ones. */
const placement = computed(() => {
  const counts = new Map<string, number>()
  for (const workload of services.value) {
    if (workload.nodeId) {
      counts.set(workload.nodeId, (counts.get(workload.nodeId) ?? 0) + 1)
    }
  }
  const placed = [...counts.values()].reduce((sum, count) => sum + count, 0)
  return [...counts.entries()]
      .map(([nodeId, count]) => ({ nodeId, name: nodeNames.value[nodeId] ?? nodeId, count, percent: Math.round(count / placed * 100) }))
      .sort((a, b) => b.count - a.count)
})

const runSummary = computed(() => {
  const count = (status: ExecutionStatus) => deployRuns.value.filter(run => run.status === status).length
  return [
    { label: 'Completed', icon: markRaw(CircleCheck), tint: TINTS.green, count: count(ExecutionStatus.COMPLETED) },
    { label: 'Failed', icon: markRaw(CircleX), tint: TINTS.red, count: count(ExecutionStatus.FAILED) },
    { label: 'Cancelled', icon: markRaw(Ban), tint: TINTS.orange, count: count(ExecutionStatus.CANCELLED) },
    { label: 'Running', icon: markRaw(LoaderCircle), tint: TINTS.sky, count: count(ExecutionStatus.RUNNING) }
  ]
})

function shaOf(run: JobRun): string {
  const sha = commitShaOf(run)
  return sha ? shortSha(sha) : '—'
}

function openRun(run: JobRun) {
  router.push(`${basePath.value}/jobs/${encodeURIComponent(run.id ?? '')}`)
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const [runs, workloadList, nodeList] = await Promise.all([
      scanJobRuns(scope.value),
      scanWorkloads(scope.value),
      loadNodes()
    ])
    deployRuns.value = runs.filter(isDeployRun)
    workloads.value = workloadList
    nodes.value = nodeList
  } catch (err) {
    error.value = errorMessage(err, 'Failed to load the deployment')
  } finally {
    loading.value = false
  }
}

// The header's switchers navigate in place, so the router reuses this instance across scopes
watch(() => [props.organizationId, props.applicationId, props.projectId], load, { immediate: true })
</script>
