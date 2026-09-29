<template>
  <div class="flex flex-col">
    <PageHeader title="Deployment" />

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <div v-if="!loading && !deployment" class="flex flex-col items-center rounded-xl border border-dashed border-surface-300 px-6 py-14 text-center dark:border-surface-700">
      <span :class="['flex h-12 w-12 items-center justify-center rounded-xl', TINTS.sky]">
        <CloudUpload :size="24" :stroke-width="1.75" aria-hidden="true" />
      </span>
      <p class="mt-4 text-sm font-medium text-surface-950 dark:text-surface-0">Never deployed</p>
      <p class="mt-1 text-sm text-muted-color">Pushing to the repository's default branch deploys this project.</p>
    </div>

    <template v-if="deployment">
      <div class="mb-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard :icon="CloudUpload" :tint="TINTS.sky" label="Status"
                  :detail="deployment.updated ? `Updated ${DatetimeUtil.formatRelativeDate(deployment.updated)}` : undefined">
          <Tag :value="phase ?? 'UNKNOWN'" :severity="phase ? deploymentStatusSeverity(phase) : 'secondary'" />
        </StatCard>
        <StatCard :icon="GitCommitHorizontal" :tint="TINTS.green" label="Live commit"
                  :detail="deployingCommit ? `deploying ${shortSha(deployingCommit)}` : 'the commit being served'">
          <span class="font-mono text-2xl font-semibold tracking-tight text-surface-950 dark:text-surface-0"
                v-tooltip.top="liveCommit ?? undefined">{{ liveCommit ? shortSha(liveCommit) : '—' }}</span>
        </StatCard>
        <StatCard :icon="Server" :tint="TINTS.orange" label="Microservices" :value="microservices.length"
                  detail="each running in a VM of its own" />
        <StatCard :icon="Globe" :tint="TINTS.purple" label="UIs" :value="uis.length"
                  detail="each served from a site of its own" />
      </div>

      <Message v-if="phase === StatusType.FAILED && deployment.failureMessage"
               severity="error" :closable="false" class="mb-4">{{ deployment.failureMessage }}</Message>

      <div class="mb-4 grid gap-4 lg:grid-cols-3">
        <DashboardSection :icon="Activity" :tint="TINTS.green" title="Workloads by status" :count="workloads.length">
          <div class="flex flex-col gap-4 p-5">
            <div v-for="bucket in statusBuckets" :key="bucket.label" :class="['border-l-[3px] pl-3', bucket.border]">
              <div class="text-2xl font-semibold leading-7 tabular-nums text-surface-950 dark:text-surface-0">{{ bucket.count }}</div>
              <div class="text-xs text-muted-color">{{ bucket.label }}</div>
            </div>
          </div>
        </DashboardSection>

        <DashboardSection :icon="GitCommitHorizontal" :tint="TINTS.sky" title="On the live commit"
                          :description="liveCommit ? `Workloads serving ${shortSha(liveCommit)}` : 'No commit is live yet'">
          <div class="p-5">
            <div class="flex items-end justify-between gap-4">
              <div class="min-w-0 flex-1">
                <div class="text-xs text-muted-color">{{ onCommit.total.current }} of {{ onCommit.total.all }} workloads</div>
                <div class="mt-2 h-2 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700">
                  <div class="h-full rounded-full bg-sky-500" :style="{ width: `${onCommit.total.percent}%` }" />
                </div>
              </div>
              <div class="text-4xl font-light tabular-nums tracking-tight text-surface-950 dark:text-surface-0">{{ onCommit.total.percent }}%</div>
            </div>
            <div class="mt-5 divide-y divide-surface-100 border-t border-surface-100 text-sm dark:divide-surface-800 dark:border-surface-800">
              <div v-for="row in onCommit.rows" :key="row.label" class="flex items-center gap-3 py-2.5">
                <component :is="row.icon" :size="16" :stroke-width="1.75" class="shrink-0 text-surface-400" aria-hidden="true" />
                <span class="w-24 shrink-0 text-surface-800 dark:text-surface-100">{{ row.label }}</span>
                <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700">
                  <div class="h-full rounded-full bg-sky-500" :style="{ width: `${row.percent}%` }" />
                </div>
                <span class="w-10 text-right text-xs tabular-nums text-muted-color">{{ row.percent }}%</span>
                <span class="w-12 text-right text-xs font-medium tabular-nums text-surface-800 dark:text-surface-100">{{ row.current }} of {{ row.all }}</span>
              </div>
            </div>
          </div>
        </DashboardSection>

        <DashboardSection :icon="KeyRound" :tint="TINTS.red" title="Machine identities" :count="machines.length">
          <div class="flex flex-col gap-4 p-5">
            <div v-for="row in machineSummary" :key="row.label" class="flex items-center gap-3">
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
        <DashboardSection v-if="deployment.lastJobRunId" :icon="JobsIcon" :tint="TINTS.blue" title="Latest deployment run"
                          description="Each step of the run the last push started, live while it runs; open a step for its detail.">
          <div class="p-5">
          <JobRunProgress :key="deployment.lastJobRunId"
                          :job-run-id="deployment.lastJobRunId"
                          :expandable="ProjectDeployStores.hasDetail" :task-icon="ProjectDeployStores.iconOf">
            <template #detail="{ node, root }">
              <ProjectDeployTaskDetail :organization-id="organizationId" :node="node" :root="root" />
            </template>
          </JobRunProgress>
          </div>
        </DashboardSection>

        <DashboardSection :icon="Server" :tint="TINTS.orange" title="Microservices" :count="microservices.length"
                          description="Each microservice the deployment has ensured runs in a VM of its own, kept running by the platform: a VM that exits is replaced. Restart stops the VM and a fresh one takes its place; Remove stops the VM and deletes its machine identity — a microservice the current commit still contains comes back with the next deployment.">
          <MicroserviceDeploymentsTable v-if="microservices.length" :deployments="microservices"
                                        @logs="openLogs" @restart="confirmRestart" @remove="confirmRemove" />
          <p v-else class="px-5 py-4 text-sm text-muted-color">No microservice has been deployed yet.</p>
        </DashboardSection>

        <DashboardSection :icon="Globe" :tint="TINTS.purple" title="UIs" :count="uis.length"
                          description="Each UI the deployment has published is served from a site of its own, checked until it serves the published commit. Remove takes the site down and deletes its files — a UI the current commit still contains comes back with the next deployment, at a new site.">
          <UiDeploymentsTable v-if="uis.length" :deployments="uis" @remove="confirmRemoveUi" />
          <p v-else class="px-5 py-4 text-sm text-muted-color">No UI has been published yet.</p>
        </DashboardSection>

        <DashboardSection v-if="machines.length" :icon="KeyRound" :tint="TINTS.red" title="Machine identities" :count="machines.length"
                          description="The deployment's workloads connect to Kinotic as these machines, on behalf of your organization. They are created and their secrets reissued by the deployment itself — a secret is never stored, so each one only ever exists inside the workload it was issued for.">
          <DataTable :value="machines" size="small">
            <Column field="usedFor" header="Used for" style="width: 20%" />
            <Column header="Status" style="width: 14%">
              <template #body="{ data }">
                <Tag :value="data.enabled ? 'Active' : 'Disabled'"
                     :severity="data.enabled ? 'success' : 'danger'" />
              </template>
            </Column>
            <Column field="displayName" header="Name" style="width: 26%" />
            <Column header="Client ID" style="width: 40%">
              <template #body="{ data }"><span class="font-mono text-sm">{{ data.id }}</span></template>
            </Column>
          </DataTable>
        </DashboardSection>

        <DashboardSection :icon="Clock" :tint="TINTS.sky" title="History" :count="history.length"
                          :description="`What happened to the deployment and to what it made, newest first: each push and each answer to it, each microservice VM's run, each UI's site, and each mark the platform set beside them, with what caused it. The latest ${HISTORY_PAGE_SIZE} entries.`">
          <WatchEventsTable :entries="history" show-record empty-text="Nothing has happened to the deployment yet." />
        </DashboardSection>
      </div>
    </template>

    <div v-else-if="loading" class="p-6 text-sm text-muted-color">Loading deployment…</div>

    <WorkloadLogsDialog v-if="logsFor?.workloadId"
                        v-model:visible="logsVisible"
                        :organization-id="logsFor.organizationId"
                        :workload-id="logsFor.workloadId"
                        :workload-name="logsFor.name" />
    <ConfirmDialog />
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onUnmounted, ref } from 'vue'
import Column from 'primevue/column'
import ConfirmDialog from 'primevue/confirmdialog'
import DataTable from 'primevue/datatable'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { Activity, Boxes, CircleCheck, CircleOff, Clock, CloudUpload, GitCommitHorizontal, Globe, KeyRound, Server } from '@lucide/vue'
import { DatetimeUtil, JobRunProgress, JobsIcon, PageHeader, ProjectDeployStores, ProjectDeployTaskDetail,
         WatchEventsTable, WorkloadLogsDialog, deploymentStatusSeverity, observedPhaseSeverity, shortSha, showErrorToast } from '@kinotic-ai/frontend-common'
import { Kinotic, Pageable } from '@kinotic-ai/core'
import { DeploymentStatusType,
         type MachineParticipantIdentity,
         type MicroserviceDeployment,
         type ProjectDeployment,
         type UiDeployment,
         type WatchEvent } from '@kinotic-ai/management-api'
import MicroserviceDeploymentsTable from '@/components/MicroserviceDeploymentsTable.vue'
import { KinoticStates } from '@/states'
import UiDeploymentsTable from '@/components/UiDeploymentsTable.vue'
import DashboardSection from '@/components/DashboardSection.vue'
import StatCard from '@/components/StatCard.vue'
import { TINTS } from '@/util/tints'

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
const HISTORY_PAGE_SIZE = 50

const toast = useToast()
const confirm = useConfirm()
const organizationId = KinoticStates.getUserState().getOrganizationId()
const StatusType = DeploymentStatusType

const deployment = ref<ProjectDeployment | null>(null)
/** The phase the deployment reports it is in, or null for a record with no report yet. */
const phase = computed(() => deployment.value?.state.observed?.phase ?? null)
/** The commit the deployment serves, or null while none is served. */
const liveCommit = computed(() => deployment.value?.state.observed?.commitSha ?? null)
/** The commit a deployment still in progress is rolling out, or null when none is. */
const deployingCommit = computed(() =>
    phase.value === DeploymentStatusType.DEPLOYING ? deployment.value?.state.desired?.commitSha ?? null : null)
const microservices = ref<MicroserviceDeployment[]>([])
const uis = ref<UiDeployment[]>([])
const machines = ref<MachineRow[]>([])
const history = ref<WatchEvent[]>([])
const loading = ref(true)

/** The phase groups a workload's status falls in, worst last, with the colour of each. */
const STATUS_BUCKETS = [
  { severity: 'success', label: 'Running', border: 'border-green-500' },
  { severity: 'info', label: 'In progress', border: 'border-sky-500' },
  { severity: 'warn', label: 'Needs attention', border: 'border-amber-500' },
  { severity: 'danger', label: 'Failed', border: 'border-red-500' },
  { severity: 'secondary', label: 'Pending', border: 'border-surface-300 dark:border-surface-600' }
]

const workloads = computed(() => [...microservices.value, ...uis.value])
/** How many microservices and UIs report a phase in each bucket; buckets past Failed show only when used. */
const statusBuckets = computed(() => {
  const severities = workloads.value.map(workload => observedPhaseSeverity(workload.state.observed))
  return STATUS_BUCKETS
      .map(bucket => ({ ...bucket, count: severities.filter(severity => severity === bucket.severity).length }))
      .filter(bucket => bucket.count > 0 || bucket.severity !== 'secondary')
})

/** How many workloads of each kind serve the live commit, as counts and a rounded percentage. */
const onCommit = computed(() => {
  const share = (list: (MicroserviceDeployment | UiDeployment)[]) => {
    const current = liveCommit.value ? list.filter(item => item.state.observed?.commitSha === liveCommit.value).length : 0
    return { current, all: list.length, percent: list.length ? Math.round(current / list.length * 100) : 0 }
  }
  return {
    total: share(workloads.value),
    rows: [
      { label: 'Microservices', icon: markRaw(Server), ...share(microservices.value) },
      { label: 'UIs', icon: markRaw(Globe), ...share(uis.value) }
    ]
  }
})

const machineSummary = computed(() => {
  const active = machines.value.filter(machine => machine.enabled).length
  return [
    { label: 'Active, connecting on the organization\'s behalf', icon: markRaw(CircleCheck), tint: TINTS.green, count: active },
    { label: 'Disabled', icon: markRaw(CircleOff), tint: TINTS.red, count: machines.value.length - active },
    { label: 'Issued to microservices', icon: markRaw(Boxes), tint: TINTS.orange,
      count: microservices.value.filter(microservice => microservice.machineIdentityId).length }
  ]
})
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
    // keeps being checked after the run, so those are watched until they are reconciled
    if (deployment.value !== null && deployment.value.lastJobRunId !== previousJobRunId) {
      await loadDetails()
    } else if (uis.value.some(ui => !ui.state.reconciled)) {
      await loadUis()
    }
    // every poll re-reads the ledger: the workers write to it between runs, a restart or a removal
    // included, and the page is what shows their answers
    if (deployment.value !== null) {
      await loadHistory()
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

async function loadHistory(): Promise<void> {
  const page = await Kinotic.projects.findDeploymentHistory(props.projectId, Pageable.create(0, HISTORY_PAGE_SIZE))
  history.value = page.content ?? []
}

function openLogs(microservice: MicroserviceDeployment): void {
  logsFor.value = microservice
  logsVisible.value = true
}

function confirmRestart(microservice: MicroserviceDeployment): void {
  confirm.require({
    header: 'Restart microservice',
    message: `Restart ${microservice.name}? Its VM is stopped and a fresh one started, and the service is unavailable meanwhile.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Restart', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: () => run(() => Kinotic.microserviceDeployments.restart(microservice.id!),
                      `${microservice.name} restarted`, `Failed to restart ${microservice.name}`)
  })
}

function confirmRemove(microservice: MicroserviceDeployment): void {
  confirmRemoval(microservice.name, microservice.state.observed?.phase === DeploymentStatusType.ORPHANED,
                 'Remove microservice',
                 `Remove ${microservice.name}? Its VM is stopped and its machine identity deleted. The next deployment brings it back while the commit still contains it.`,
                 () => Kinotic.microserviceDeployments.remove(microservice.id!))
}

function confirmRemoveUi(ui: UiDeployment): void {
  confirmRemoval(ui.name, ui.state.observed?.phase === DeploymentStatusType.ORPHANED, 'Remove UI',
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
