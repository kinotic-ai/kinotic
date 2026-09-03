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

      <section class="mt-8">
        <h2 class="text-base font-medium mb-1">Microservices</h2>
        <p class="text-sm text-muted-color mt-0 mb-3">
          Each microservice the deployment has ensured runs in a VM of its own. Restart boots
          the VM again in place; Remove destroys the VM and its machine identity — a
          microservice the current commit still contains comes back with the next deployment.
        </p>
        <MicroserviceDeploymentsTable v-if="microservices.length" :deployments="microservices"
                                      @logs="openLogs" @restart="confirmRestart" @remove="confirmRemove" />
        <div v-else class="text-sm text-muted-color">No microservice has been deployed yet.</div>
      </section>

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

    <WorkloadLogsDialog v-if="logsFor?.workloadId"
                        v-model:visible="logsVisible"
                        :workload-id="logsFor.workloadId"
                        :workload-name="logsFor.name" />
    <ConfirmDialog />
  </div>
</template>

<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Column from 'primevue/column'
import ConfirmDialog from 'primevue/confirmdialog'
import DataTable from 'primevue/datatable'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { DatetimeUtil, JobRunProgress, PageHeader, ProjectDeployStores, ProjectDeployTaskDetail,
         WorkloadLogsDialog, showErrorToast } from '@kinotic-ai/frontend-common'
import { Kinotic } from '@kinotic-ai/core'
import { MicroserviceDeploymentStatusType,
         ProjectDeploymentStatusType,
         type MachineParticipantIdentity,
         type MicroserviceDeployment,
         type ProjectDeployment } from '@kinotic-ai/management-api'
import MicroserviceDeploymentsTable from '@/components/MicroserviceDeploymentsTable.vue'

/** One row — a machine the deployment provisioned, labelled by the workload it authenticates. */
interface MachineRow extends MachineParticipantIdentity {
  usedFor: string
}

/**
 * The project's deployment: current status and commit, the latest deployment job's tasks
 * rendered live by JobRunProgress with the build log and the artifacts on their rows, the
 * microservices it has ensured with their log, restart and removal, and the machine
 * identities its workloads connect as.
 * Polls the deployment record so a new push swaps in its job run while the page is open.
 */
const props = defineProps<{
  applicationId: string
  projectId: string
}>()

const POLL_INTERVAL_MS = 5000

const router = useRouter()
const toast = useToast()
const confirm = useConfirm()
const StatusType = ProjectDeploymentStatusType

const deployment = ref<ProjectDeployment | null>(null)
const microservices = ref<MicroserviceDeployment[]>([])
const machines = ref<MachineRow[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const logsFor = ref<MicroserviceDeployment | null>(null)
const logsVisible = ref(false)

async function loadDeployment(): Promise<void> {
  try {
    const previousJobRunId = deployment.value?.lastJobRunId
    deployment.value = await Kinotic.projects.findDeployment(props.projectId)
    error.value = null
    // a deployment ensures the microservices and provisions the machines it needs, so both
    // listings only change with a run, or with an action taken here
    if (deployment.value !== null && deployment.value.lastJobRunId !== previousJobRunId) {
      await loadMicroservicesAndMachines()
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

async function loadMicroservicesAndMachines(): Promise<void> {
  microservices.value = await Kinotic.microserviceDeployments.findAllForProject(props.projectId)
  // each machine is labelled by the deployment record that names it
  const usedFor = new Map<string, string>()
  if (deployment.value?.syncMachineIdentityId) {
    usedFor.set(deployment.value.syncMachineIdentityId, 'Checkout and entity sync')
  }
  for (const microservice of microservices.value) {
    if (microservice.machineIdentityId) {
      usedFor.set(microservice.machineIdentityId, `Microservice ${microservice.name}`)
    }
  }
  const listed = await Kinotic.machines.findProjectMachines(props.projectId)
  machines.value = listed.map(machine => ({ ...machine, usedFor: (machine.id && usedFor.get(machine.id)) ?? '' }))
}

function openLogs(microservice: MicroserviceDeployment): void {
  logsFor.value = microservice
  logsVisible.value = true
}

function confirmRestart(microservice: MicroserviceDeployment): void {
  confirm.require({
    header: 'Restart microservice',
    message: `Restart the VM of ${microservice.name}? It boots again in place and the service is unavailable meanwhile.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Restart', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: () => run(() => Kinotic.microserviceDeployments.restart(microservice.id!),
                      `${microservice.name} restarted`, `Failed to restart ${microservice.name}`)
  })
}

function confirmRemove(microservice: MicroserviceDeployment): void {
  // an orphaned microservice is one the commit already dropped, so retiring it needs no confirmation
  if (microservice.status.type === MicroserviceDeploymentStatusType.ORPHANED) {
    void run(() => Kinotic.microserviceDeployments.remove(microservice.id!),
             `${microservice.name} removed`, `Failed to remove ${microservice.name}`)
  } else {
    confirm.require({
      header: 'Remove microservice',
      message: `Remove ${microservice.name}? Its VM is destroyed and its machine identity deleted. The next deployment brings it back while the commit still contains it.`,
      icon: 'pi pi-exclamation-triangle',
      acceptProps: { label: 'Remove', severity: 'danger' },
      rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
      accept: () => run(() => Kinotic.microserviceDeployments.remove(microservice.id!),
                        `${microservice.name} removed`, `Failed to remove ${microservice.name}`)
    })
  }
}

async function run(action: () => Promise<unknown>, success: string, failure: string): Promise<void> {
  try {
    await action()
    toast.add({ severity: 'success', summary: success, life: 3000 })
  } catch (err) {
    showErrorToast(toast, failure, err, { life: 8000 })
  }
  try {
    await loadMicroservicesAndMachines()
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  }
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
