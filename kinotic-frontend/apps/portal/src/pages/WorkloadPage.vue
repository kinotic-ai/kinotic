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
        <Button v-if="deploymentPath" label="Open deployment" icon="pi pi-cloud-upload" severity="secondary" outlined
                @click="router.push(deploymentPath)" />
        <Button label="View logs" icon="pi pi-align-left" severity="secondary" outlined @click="tab = 'logs'" />
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
              <template v-if="deploymentPath">A microservice is restarted from its project's Deployment page.</template>
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

import { Kinotic, Pageable } from '@kinotic-ai/core'
import { WorkloadStatus, type Workload } from '@kinotic-ai/management-api'
import { DatetimeUtil, PageHeader, StatTile, TelemetryPanel, WorkloadDetails, WorkloadLogView, belongsToProject, errorMessage,
         formatCpus, formatMb, workloadRun, workloadSeverity, type Stat, type ViewScope } from '@kinotic-ai/frontend-common'

import { USER_STATE } from '@/states/IUserState'
import { applicationPath, projectPath, scopePath } from '@/util/scope'

/**
 * One workload of the organization, opened from a Workloads list: its state with the exit code,
 * whose it is, how long it has run, everything its record holds short of secret values, its
 * logs, and — when it ships them — its traces and metrics. A workload a project's deployment
 * started leads to that project's Deployment page. The eyebrow leads back to the list it was
 * opened from.
 */
const props = defineProps<{
  workloadId: string
  applicationId?: string
  projectId?: string
}>()

/** How many of the application's projects the page reads to find the one that started the workload. */
const PROJECT_PAGE_SIZE = 200

const route = useRoute()
const router = useRouter()
const formatEpochDateTime = DatetimeUtil.formatEpochDateTime

const scope = computed<ViewScope>(() => ({
  organizationId: USER_STATE.getOrganizationId(),
  applicationId: props.applicationId,
  projectId: props.projectId
}))

const listPath = computed(() => `${scopePath(scope.value)}/workloads`)

const workload = ref<Workload | null>(null)
/** The project whose deployment started the workload, when one did. */
const projectId = ref<string | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

// The tab lives in the URL so a link can open the logs directly
const tab = computed<string>({
  get: () => {
    const value = route.query.tab
    return value === 'logs' || value === 'telemetry' ? value : 'overview'
  },
  set: value => { router.replace({ query: { ...route.query, tab: value === 'overview' ? undefined : value } }) }
})

const deploymentPath = computed(() => workload.value?.applicationId && projectId.value
    ? `${projectPath(workload.value.applicationId, projectId.value)}/deployment`
    : null)

const stats = computed<Stat[]>(() => {
  const w = workload.value
  if (!w) return []
  const run = workloadRun(w)
  const started = run?.started ?? null
  const finished = run?.finished ?? null
  let owner: Stat
  if (w.applicationId && projectId.value) {
    owner = {
      label: 'Owner',
      value: projectId.value,
      description: `project of ${w.applicationId}`,
      to: projectPath(w.applicationId, projectId.value),
      icon: 'pi-folder',
      accent: 'teal'
    }
  } else if (w.applicationId) {
    owner = {
      label: 'Owner',
      value: w.applicationId,
      description: 'application',
      to: applicationPath(w.applicationId),
      icon: 'pi-th-large',
      accent: 'teal'
    }
  } else {
    owner = {
      label: 'Owner',
      value: 'organization',
      description: 'runs for the organization itself',
      icon: 'pi-building',
      accent: 'teal'
    }
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
    owner,
    {
      label: 'Resources',
      value: `${formatCpus(w.cpus)} CPU`,
      description: `${formatMb(w.memoryMb)} memory · ${formatMb(w.diskSizeMb)} disk`,
      icon: 'pi-microchip',
      accent: 'sky'
    },
    {
      label: 'Run time',
      value: DatetimeUtil.formatDuration(started, finished),
      description: finished !== null ? `ended ${formatEpochDateTime(finished)}` : `started ${formatEpochDateTime(started)}`,
      icon: 'pi-clock',
      accent: 'violet'
    }
  ]
})

// Project ids can prefix one another, so of the projects whose naming the workload matches, the
// longest id is the one the deployment named it after
async function resolveProject(found: Workload): Promise<string | null> {
  let ret: string | null
  if (props.projectId) {
    ret = props.projectId
  } else if (found.applicationId) {
    const page = await Kinotic.projects.findAllForApplication(found.applicationId, Pageable.create(0, PROJECT_PAGE_SIZE))
    ret = (page.content ?? []).map(project => project.id ?? '')
                              .filter(id => id !== '' && belongsToProject(found, id))
                              .sort((a, b) => b.length - a.length)[0] ?? null
  } else {
    ret = null
  }
  return ret
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const found = await Kinotic.workloadMonitoring.findWorkload(props.workloadId)
    workload.value = found
    projectId.value = await resolveProject(found).catch(() => null)
  } catch (err) {
    error.value = errorMessage(err, 'Failed to load the workload')
  } finally {
    loading.value = false
  }
}

watch(() => props.workloadId, load, { immediate: true })
</script>
