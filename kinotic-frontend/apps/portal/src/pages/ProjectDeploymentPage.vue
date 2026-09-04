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
              :title="deployment.commitSha">{{ shortSha(deployment.commitSha) }}</span>
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

      <section class="mt-8">
        <h2 class="text-base font-medium mb-1">UIs</h2>
        <p class="text-sm text-muted-color mt-0 mb-3">
          Each UI the deployment has published is served from a site of its own. A failed site
          can be provisioned again; Remove takes the site down and deletes its files — a UI the
          current commit still contains comes back with the next deployment, at a new site.
        </p>
        <UiDeploymentsTable v-if="uis.length" :deployments="uis" @retry="retryUi" @remove="confirmRemoveUi" />
        <div v-else class="text-sm text-muted-color">No UI has been published yet.</div>
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
         WorkloadLogsDialog, deploymentStatusSeverity, shortSha, showErrorToast } from '@kinotic-ai/frontend-common'
import { Kinotic } from '@kinotic-ai/core'
import { DeploymentStatusType,
         type MachineParticipantIdentity,
         type MicroserviceDeployment,
         type ProjectDeployment,
         type UiDeployment } from '@kinotic-ai/management-api'
import MicroserviceDeploymentsTable from '@/components/MicroserviceDeploymentsTable.vue'
import UiDeploymentsTable from '@/components/UiDeploymentsTable.vue'

/** One row — a machine the deployment provisioned, labelled by the workload it authenticates. */
interface MachineRow extends MachineParticipantIdentity {
  usedFor: string
}

/**
 * The project's deployment: current status and commit, the latest deployment job's tasks
 * rendered live by JobRunProgress with the build log and the artifacts on their rows, the
 * microservices it has ensured with their log, restart and removal, the UIs it has published
 * with their sites, retry and removal, and the machine identities its workloads connect as.
 * Polls the deployment record so a new push swaps in its job run while the page is open, and
 * the UIs while a site is still provisioning.
 */
const props = defineProps<{
  applicationId: string
  projectId: string
}>()

const POLL_INTERVAL_MS = 5000

const router = useRouter()
const toast = useToast()
const confirm = useConfirm()
const StatusType = DeploymentStatusType

const deployment = ref<ProjectDeployment | null>(null)
const microservices = ref<MicroserviceDeployment[]>([])
const uis = ref<UiDeployment[]>([])
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
    // a deployment ensures the microservices, publishes the UIs and provisions the machines it
    // needs, so the listings only change with a run, or with an action taken here; a site
    // keeps provisioning after the run, so those are watched until they settle
    if (deployment.value !== null && deployment.value.lastJobRunId !== previousJobRunId) {
      await loadDetails()
    } else if (uis.value.some(ui => ui.status.type === DeploymentStatusType.PROVISIONING)) {
      await loadUis()
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

async function loadDetails(): Promise<void> {
  const [listedUis, listedMicroservices, listedMachines] = await Promise.all([
    Kinotic.uiDeployments.findAllForProject(props.projectId),
    Kinotic.microserviceDeployments.findAllForProject(props.projectId),
    Kinotic.machines.findProjectMachines(props.projectId),
  ])
  uis.value = listedUis
  microservices.value = listedMicroservices
  // each machine is labelled by the deployment record that names it
  const usedFor = new Map<string, string>()
  if (deployment.value?.syncMachineIdentityId) {
    usedFor.set(deployment.value.syncMachineIdentityId, 'Checkout and entity sync')
  }
  for (const microservice of listedMicroservices) {
    if (microservice.machineIdentityId) {
      usedFor.set(microservice.machineIdentityId, `Microservice ${microservice.name}`)
    }
  }
  machines.value = listedMachines.map(machine => ({ ...machine, usedFor: (machine.id && usedFor.get(machine.id)) ?? '' }))
}

async function loadUis(): Promise<void> {
  uis.value = await Kinotic.uiDeployments.findAllForProject(props.projectId)
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
  confirmRemoval(microservice.name, microservice.status.type === DeploymentStatusType.ORPHANED,
                 'Remove microservice',
                 `Remove ${microservice.name}? Its VM is destroyed and its machine identity deleted. The next deployment brings it back while the commit still contains it.`,
                 () => Kinotic.microserviceDeployments.remove(microservice.id!))
}

function retryUi(ui: UiDeployment): void {
  void run(() => Kinotic.uiDeployments.retryProvisioning(ui.id!),
           `Provisioning ${ui.name} again`, `Failed to provision ${ui.name} again`)
}

function confirmRemoveUi(ui: UiDeployment): void {
  confirmRemoval(ui.name, ui.status.type === DeploymentStatusType.ORPHANED, 'Remove UI',
                 `Remove ${ui.name}? Its site is taken down and its files deleted. The next deployment publishes it again, at a new site, while the commit still contains it.`,
                 () => Kinotic.uiDeployments.remove(ui.id!))
}

function confirmRemoval(name: string, orphaned: boolean, header: string, message: string, action: () => Promise<void>): void {
  const remove = () => run(action, `${name} removed`, `Failed to remove ${name}`)
  // an orphaned artifact is one the commit already dropped, so retiring it needs no confirmation
  if (orphaned) {
    void remove()
  } else {
    confirm.require({
      header,
      message,
      icon: 'pi pi-exclamation-triangle',
      acceptProps: { label: 'Remove', severity: 'danger' },
      rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
      accept: remove
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
    await loadDetails()
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  }
}

const pollTimer = setInterval(() => { void loadDeployment() }, POLL_INTERVAL_MS)
onUnmounted(() => clearInterval(pollTimer))
void loadDeployment()
</script>
