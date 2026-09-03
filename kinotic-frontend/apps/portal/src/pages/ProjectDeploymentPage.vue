<template>
  <div class="flex flex-col">
    <PageHeader title="Deployment">
      <template #actions>
        <Button v-if="deployment?.lastJobRunId"
                label="View in jobs" icon="pi pi-external-link" severity="secondary" outlined
                @click="router.push({ name: 'job-run', params: { jobRunId: deployment.lastJobRunId } })" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <div v-if="!loading && !deployment" class="p-6 text-sm text-muted-color">
      This project has never been deployed. Pushing to its repository's default branch deploys it.
    </div>

    <template v-if="deployment">
      <div class="mb-4 flex flex-wrap items-center gap-4">
        <Tag :value="deployment.status.type" :severity="deploymentStatusSeverity(deployment.status.type)" />
        <span v-if="deployment.commitSha" class="font-mono text-sm text-muted-color"
              :title="deployment.commitSha">{{ deployment.commitSha.slice(0, 12) }}</span>
        <span v-if="deployment.updated" class="text-xs text-muted-color">
          Updated {{ DatetimeUtil.formatRelativeDate(deployment.updated) }}
        </span>
      </div>

      <Message v-if="deployment.status.type === StatusType.FAILED && deployment.status.message"
               severity="error" :closable="false">{{ deployment.status.message }}</Message>

      <JobRunProgress v-if="deployment.lastJobRunId"
                      :key="deployment.lastJobRunId"
                      :job-run-id="deployment.lastJobRunId"
                      :expandable="ProjectDeployStores.hasDetail">
        <template #detail="{ node, root }">
          <ProjectDeployTaskDetail :node="node" :root="root" />
        </template>
      </JobRunProgress>

      <section v-if="machines.length" class="mt-8">
        <h2 class="text-base font-medium mb-1">Machine identities</h2>
        <p class="text-sm text-muted-color mt-0 mb-3">
          The deployment's workloads connect to Kinotic as these machines, on behalf of your
          organization. They are created and their secrets reissued by the deployment itself —
          a secret is never stored, so each one only ever exists inside the workload it was
          issued for.
        </p>
        <DataTable :value="machines" size="small">
          <Column field="usedFor" header="Used for" style="width: 26%" />
          <Column field="displayName" header="Name" style="width: 26%" />
          <Column header="Client ID" style="width: 32%">
            <template #body="{ data }"><span class="font-mono text-sm">{{ data.id }}</span></template>
          </Column>
          <Column header="Status" style="width: 16%">
            <template #body="{ data }">
              <Tag :value="data.enabled ? 'Active' : 'Disabled'"
                   :severity="data.enabled ? 'success' : 'danger'" />
            </template>
          </Column>
        </DataTable>
      </section>
    </template>

    <div v-else-if="loading" class="p-6 text-sm text-muted-color">Loading deployment…</div>
  </div>
</template>

<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { DatetimeUtil, JobRunProgress, PageHeader, ProjectDeployStores, ProjectDeployTaskDetail } from '@kinotic-ai/frontend-common'
import { Kinotic } from '@kinotic-ai/core'
import { ProjectDeploymentStatusType,
         type MachineParticipantIdentity,
         type ProjectDeployment } from '@kinotic-ai/management-api'

/** One row — a machine the deployment provisioned, labelled by the workload it authenticates. */
interface MachineRow extends MachineParticipantIdentity {
  usedFor: string
}

/**
 * The project's deployment: current status and commit, the latest deployment job's tasks
 * rendered live by JobRunProgress with the build log and the artifacts on their rows, and
 * the machine identities its workloads connect as.
 * Polls the deployment record so a new push swaps in its job run while the page is open.
 */
const props = defineProps<{
  applicationId: string
  projectId: string
}>()

const POLL_INTERVAL_MS = 5000

const router = useRouter()
const StatusType = ProjectDeploymentStatusType

const deployment = ref<ProjectDeployment | null>(null)
const machines = ref<MachineRow[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

async function loadDeployment(): Promise<void> {
  try {
    const previousJobRunId = deployment.value?.lastJobRunId
    deployment.value = await Kinotic.projects.findDeployment(props.projectId)
    error.value = null
    // a deployment provisions the machines it needs, so the listing only changes with a run
    if (deployment.value !== null && deployment.value.lastJobRunId !== previousJobRunId) {
      await loadMachines()
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

async function loadMachines(): Promise<void> {
  // findProjectMachines returns the deployment's machines in the order it records them, so
  // position is what says which workload each one authenticates
  const listed = await Kinotic.machines.findProjectMachines(props.projectId)
  const usedFor = ['Checkout and entity sync', 'Microservice runtime']
  machines.value = listed.map((machine, index) => ({ ...machine, usedFor: usedFor[index] ?? '' }))
}

function deploymentStatusSeverity(type: ProjectDeploymentStatusType): string {
  let ret: string
  if (type === ProjectDeploymentStatusType.RUNNING) {
    ret = 'success'
  } else if (type === ProjectDeploymentStatusType.FAILED) {
    ret = 'danger'
  } else {
    ret = 'info'
  }
  return ret
}

const pollTimer = setInterval(() => { void loadDeployment() }, POLL_INTERVAL_MS)
onUnmounted(() => clearInterval(pollTimer))
void loadDeployment()
</script>
