<template>
  <div class="flex flex-col">
    <PageHeader title="Deployment">
      <template #actions>
        <Button label="All runs" icon="pi pi-list-check" severity="secondary" outlined @click="router.push(`${basePath}/jobs`)" />
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div v-if="!loading && !latestRun" class="p-6 text-sm text-muted-color">
      This project has never been deployed. Pushing to its repository's default branch deploys it.
    </div>

    <template v-if="latestRun">
      <div class="mb-4 flex flex-wrap items-center gap-4">
        <Tag :value="latestRun.status" :severity="executionStatusSeverity(latestRun.status)" />
        <span v-if="latestSha" class="font-mono text-sm text-muted-color" :title="latestSha">{{ shortSha(latestSha) }}</span>
        <span v-if="latestRun.started" class="text-xs text-muted-color">Started {{ formatEpochDateTime(latestRun.started) }}</span>
      </div>

      <Message v-if="latestRun.status === ExecutionStatus.FAILED && latestRun.error" severity="error" :closable="false" class="mb-4">
        {{ latestRun.error }}
      </Message>

      <JobRunProgress :key="latestRun.id ?? ''" :job-run-id="latestRun.id ?? ''" :expandable="ProjectDeployStores.hasDetail">
        <template #detail="{ node, root }">
          <ProjectDeployTaskDetail :node="node" :root="root" />
        </template>
      </JobRunProgress>

      <section class="mt-8">
        <h2 class="mb-1 text-base font-medium">Runtime workloads</h2>
        <p class="mt-0 mb-3 text-sm text-muted-color">
          The microservice VMs this project's deployments have left running, where the platform
          placed them, and what an operator can do about each.
        </p>
        <WorkloadsTable v-if="services.length > 0" :workloads="services" :scope="scope" :node-names="nodeNames" @changed="load" />
        <div v-else class="text-sm text-muted-color">No microservice workload is running for this project.</div>
      </section>

      <section class="mt-8">
        <h2 class="mb-1 text-base font-medium">Previous runs</h2>
        <p class="mt-0 mb-3 text-sm text-muted-color">Earlier deployments of this project. Open one to see its tasks.</p>
        <DataTable v-if="previousRuns.length > 0" :value="previousRuns" size="small" class="text-sm" row-hover
                   @row-click="openRun($event.data)">
          <Column header="Status" style="width: 10rem">
            <template #body="{ data }"><Tag :value="data.status" :severity="executionStatusSeverity(data.status)" /></template>
          </Column>
          <Column header="Commit">
            <template #body="{ data }"><span class="font-mono text-xs">{{ shaOf(data) }}</span></template>
          </Column>
          <Column header="Started" class="hidden md:table-cell">
            <template #body="{ data }">{{ formatEpochDateTime(data.started) }}</template>
          </Column>
          <Column header="Duration" style="width: 8rem">
            <template #body="{ data }">{{ formatDuration(data.started, data.finished) }}</template>
          </Column>
        </DataTable>
        <p v-else class="m-0 text-sm text-muted-color">No previous runs.</p>
      </section>
    </template>

    <div v-else-if="loading" class="p-6 text-sm text-muted-color">Loading deployment…</div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Message from 'primevue/message'
import Tag from 'primevue/tag'

import { ExecutionStatus, type JobRun, type Workload } from '@kinotic-ai/management-api'
import type { VmNode } from '@kinotic-ai/system-api'
import { DatetimeUtil, JobRunProgress, PageHeader, ProjectDeployStores, ProjectDeployTaskDetail,
         errorMessage, executionStatusSeverity, scanJobRuns, shortSha } from '@kinotic-ai/frontend-common'

import WorkloadsTable from '@/components/WorkloadsTable.vue'
import { loadNodes } from '@/util/nodes'
import { commitShaOf, isDeployRun } from '@/util/runs'
import { projectPath, type Scope } from '@/util/scope'
import { scanWorkloads } from '@/util/workloads'

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

const router = useRouter()
const formatEpochDateTime = DatetimeUtil.formatEpochDateTime
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
