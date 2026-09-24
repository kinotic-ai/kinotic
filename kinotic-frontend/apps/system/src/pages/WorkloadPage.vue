<template>
  <div class="flex flex-col">
    <PageHeader :title="workload?.name ?? workloadId">
      <template #eyebrow>
        <RouterLink :to="listPath" class="hover:underline">Workloads</RouterLink>
        <i class="pi pi-chevron-right" :style="{ fontSize: '10px' }" />
        <span class="truncate">{{ workload?.name ?? workloadId }}</span>
      </template>
      <template #actions>
        <Tag v-if="workload" :value="workload.status" :severity="workloadSeverity(workload.status)" />
        <Button label="View logs" icon="pi pi-align-left" severity="secondary" outlined @click="tab = 'logs'" />
        <Button v-if="canStop" label="Stop" icon="pi pi-stop-circle" severity="secondary" outlined
                @click="act(() => Kinotic.workloadOrchestration.stopWorkload(workloadId), 'Workload stopping', 'Failed to stop workload')" />
        <Button label="Destroy" icon="pi pi-trash" severity="danger" outlined :disabled="!workload" @click="confirmDestroy" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <Tabs v-model:value="tab">
      <TabList>
        <Tab value="overview"><i class="pi pi-objects-column mr-2" />Overview</Tab>
        <Tab value="logs"><i class="pi pi-align-left mr-2" />Logs</Tab>
        <Tab v-if="workload?.telemetry" value="telemetry"><i class="pi pi-chart-line mr-2" />Telemetry</Tab>
      </TabList>
      <TabPanels>
        <TabPanel value="overview">
          <div v-if="workload" class="flex flex-col gap-4 pt-2">
            <div class="grid grid-cols-2 gap-4 xl:grid-cols-4">
              <StatTile v-for="stat in stats" :key="stat.label" v-bind="stat" />
            </div>

            <Message v-if="workload.status === WorkloadStatus.FAILED" severity="error" :closable="false">
              The VM exited{{ workload.exitCode !== null ? ` with code ${workload.exitCode}` : '' }}. Its last log lines are on the Logs tab.
            </Message>

            <WorkloadDetails :workload="workload" />
          </div>
          <div v-else-if="loading" class="p-6 text-sm text-muted-color">Loading workload…</div>
        </TabPanel>
        <TabPanel value="logs">
          <!-- Mounted with the tab, so a return starts a fresh history load and tail -->
          <WorkloadLogView v-if="tab === 'logs' && workload" :organization-id="workload.organizationId" :workload-id="workloadId" :run="workloadRun(workload)" class="pt-2" />
        </TabPanel>
        <TabPanel value="telemetry">
          <!-- The traces and span metrics the workload exported; a trace opens in a dialog over them -->
          <TelemetryPanel v-if="tab === 'telemetry' && workload" :organization-id="workload.organizationId"
                          :application-id="null" :workload-id="workloadId" class="pt-2" />
        </TabPanel>
      </TabPanels>
    </Tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tab from 'primevue/tab'
import TabList from 'primevue/tablist'
import TabPanel from 'primevue/tabpanel'
import TabPanels from 'primevue/tabpanels'
import Tabs from 'primevue/tabs'
import Tag from 'primevue/tag'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'

import { Kinotic } from '@kinotic-ai/core'
import { WorkloadStatus, type Workload } from '@kinotic-ai/management-api'
import type { VmNode } from '@kinotic-ai/system-api'
import { DatetimeUtil, PageHeader, StatTile, TelemetryPanel, WorkloadDetails, WorkloadLogView, errorMessage, formatCpus,
         formatMb, showErrorToast, workloadRun, workloadSeverity, type Stat, type ViewScope } from '@kinotic-ai/frontend-common'

import { nodePath } from '@/util/nodes'
import { applicationPath, organizationPath, scopePath } from '@/util/scope'

/**
 * One workload, opened from a Workloads list: its state with the exit code, where it runs and
 * for whom, everything its record holds short of secret values, and its logs on a tab. The
 * eyebrow leads back to the list it was opened from.
 */
const props = defineProps<{
  workloadId: string
  organizationId?: string
  applicationId?: string
  projectId?: string
}>()

const route = useRoute()
const router = useRouter()
const toast = useToast()
const confirm = useConfirm()
const formatEpochDateTime = DatetimeUtil.formatEpochDateTime

const scope = computed<ViewScope>(() => ({
  organizationId: props.organizationId,
  applicationId: props.applicationId,
  projectId: props.projectId
}))

const listPath = computed(() => `${scopePath(scope.value)}/workloads`)

const workload = ref<Workload | null>(null)
const node = ref<VmNode | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

// The tab lives in the URL so a row menu can open the logs directly
const tab = computed<string>({
  get: () => {
    const value = route.query.tab
    return value === 'logs' || value === 'telemetry' ? value : 'overview'
  },
  set: value => { router.replace({ query: { ...route.query, tab: value === 'overview' ? undefined : value } }) }
})

const canStop = computed(() => workload.value?.status === WorkloadStatus.RUNNING || workload.value?.status === WorkloadStatus.STARTING)

const stats = computed<Stat[]>(() => {
  const w = workload.value
  if (!w) return []
  let ownerName: string
  let ownerDescription: string
  let ownerTo: string
  let ownerIcon: string
  if (w.organizationId && w.applicationId) {
    ownerName = w.applicationId
    ownerDescription = `application of ${w.organizationId}`
    ownerTo = applicationPath(w.organizationId, w.applicationId)
    ownerIcon = 'pi-th-large'
  } else if (w.organizationId) {
    ownerName = w.organizationId
    ownerDescription = 'the organization itself'
    ownerTo = organizationPath(w.organizationId)
    ownerIcon = 'pi-building'
  } else {
    ownerName = 'platform'
    ownerDescription = 'runs for the platform itself'
    ownerTo = '/cluster'
    ownerIcon = 'pi-shield'
  }
  return [
    {
      label: 'Status',
      value: w.status,
      description: w.exitCode !== null ? `exit code ${w.exitCode}` : `since ${formatEpochDateTime(w.updated ?? w.created)}`,
      tag: workloadSeverity(w.status),
      icon: 'pi-wave-pulse',
      accent: w.status === WorkloadStatus.FAILED ? 'red' : 'green'
    },
    {
      label: 'Node',
      value: node.value?.name ?? w.nodeId ?? '—',
      description: node.value ? `${node.value.status.type.toLowerCase()} · ${node.value.providerType}` : 'not placed yet',
      to: w.nodeId ? nodePath(w.nodeId) : undefined,
      icon: 'pi-server',
      accent: 'amber'
    },
    {
      label: 'Owner',
      value: ownerName,
      description: ownerDescription,
      to: ownerTo,
      icon: ownerIcon,
      accent: 'teal'
    },
    {
      label: 'Resources',
      value: `${formatCpus(w.cpus)} CPU`,
      description: `${formatMb(w.memoryMb)} memory · ${formatMb(w.diskSizeMb)} disk`,
      icon: 'pi-microchip',
      accent: 'sky'
    }
  ]
})

async function load() {
  loading.value = true
  error.value = null
  try {
    workload.value = await Kinotic.workloads.findById(props.workloadId)
    node.value = workload.value.nodeId ? await Kinotic.vmNodes.findById(workload.value.nodeId).catch(() => null) : null
  } catch (err) {
    error.value = errorMessage(err, 'Failed to load the workload')
  } finally {
    loading.value = false
  }
}

async function act(action: () => Promise<unknown>, successMessage: string, failureMessage: string) {
  try {
    await action()
    toast.add({ severity: 'success', summary: successMessage, life: 4000 })
    await load()
  } catch (err) {
    showErrorToast(toast, failureMessage, err, { life: 8000 })
  }
}

function confirmDestroy() {
  confirm.require({
    header: 'Confirm destroy',
    message: `Destroy workload ${workload.value?.name}? Its VM and disk are removed permanently.`,
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Destroy', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: async () => {
      try {
        await Kinotic.workloadOrchestration.destroyWorkload(props.workloadId)
        toast.add({ severity: 'success', summary: 'Workload destroyed', life: 4000 })
        router.push(listPath.value)
      } catch (err) {
        showErrorToast(toast, 'Failed to destroy workload', err, { life: 8000 })
      }
    }
  })
}

watch(() => props.workloadId, load, { immediate: true })
</script>
