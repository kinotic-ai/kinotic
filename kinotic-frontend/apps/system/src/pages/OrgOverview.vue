<template>
  <div>
    <PageHeader title="Overview" description="The organization at a glance.">
      <template #actions>
        <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loading" @click="load" />
      </template>
    </PageHeader>

    <Message v-if="error" severity="error" :closable="false" class="mb-4">{{ error }}</Message>

    <!-- The same bands as the dashboard: tiles, attention, charts, runs, then the records -->
    <div class="flex flex-col gap-4">
      <div class="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-5">
        <StatTile v-for="stat in stats" :key="stat.label" v-bind="stat" />
      </div>

      <AttentionList :items="attention" />

      <div class="grid gap-4 lg:grid-cols-2">
        <WorkloadStateCard :workloads="workloads" description="The organization's workloads." :view-all-to="`${basePath}/workloads`" />
        <JobRunsByDayChart :runs="runs" :view-all-to="`${basePath}/jobs`" />
      </div>

      <RecentRunsTable :runs="recentRuns" :scope="{ organizationId }" />

      <div class="grid gap-4 lg:grid-cols-2">
        <div class="flex flex-col gap-2 rounded-lg border border-surface p-4">
          <h2 class="text-base font-semibold">Details</h2>
          <dl class="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-sm">
            <dt class="text-muted-color">Id</dt>
            <dd class="font-mono">{{ organizationId }}</dd>
            <dt class="text-muted-color">Name</dt>
            <dd>{{ organization?.name ?? '—' }}</dd>
            <dt class="text-muted-color">Description</dt>
            <dd>{{ organization?.description || '—' }}</dd>
            <dt class="text-muted-color">Created</dt>
            <dd>{{ formatEpochDate(organization?.created ?? null) }}</dd>
            <dt class="text-muted-color">Created by</dt>
            <dd class="break-all font-mono">{{ organization?.createdBy ?? '—' }}</dd>
          </dl>
        </div>
        <div class="flex flex-col gap-2 rounded-lg border border-surface p-4">
          <div class="flex items-center justify-between gap-2">
            <h2 class="text-base font-semibold">Provisioning</h2>
            <Button label="Provision again" icon="pi pi-refresh" size="small" severity="secondary" outlined
                    :loading="provisioning" :disabled="!organization" @click="provision" />
          </div>
          <p class="mt-0 text-sm text-muted-color">
            The storage the organization's deployments publish to, and what serves its UIs from
            it, created by a job when the organization was. Provision again runs that job once
            more; it does whatever an earlier run left undone.
          </p>
          <dl class="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-sm">
            <dt class="text-muted-color">Storage</dt>
            <dd>
              <span v-if="organization?.storage" :title="organization.storage.status.message ?? undefined">
                <Tag :value="organization.storage.status.type"
                     :severity="deploymentStatusSeverity(organization.storage.status.type)" />
              </span>
              <span v-else>Not provisioned</span>
            </dd>
            <template v-if="organization?.storage?.status.message">
              <dt class="text-muted-color">Reason</dt>
              <dd class="break-words">{{ organization.storage.status.message }}</dd>
            </template>
            <dt class="text-muted-color">Account</dt>
            <dd class="font-mono">{{ organization?.storage?.azureAccountName || '—' }}</dd>
            <dt class="text-muted-color">Endpoint</dt>
            <dd class="break-all font-mono">{{ organization?.storage?.azureBlobEndpoint || '—' }}</dd>
            <dt class="text-muted-color">Last run</dt>
            <dd>
              <RouterLink v-if="organization?.provisioningJobRunId" class="font-mono text-primary hover:underline"
                          :to="`${basePath}/jobs/${encodeURIComponent(organization.provisioningJobRunId)}`">
                {{ organization.provisioningJobRunId }}
              </RouterLink>
              <span v-else>—</span>
            </dd>
          </dl>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import { ExecutionStatus, DeploymentStatusType, WorkloadStatus,
         type JobRun, type Organization, type Workload } from '@kinotic-ai/management-api'
import { DatetimeUtil, PageHeader, deploymentStatusSeverity, errorMessage, scanJobRuns, showErrorToast } from '@kinotic-ai/frontend-common'

import AttentionList from '@/components/AttentionList.vue'
import JobRunsByDayChart from '@/components/JobRunsByDayChart.vue'
import RecentRunsTable from '@/components/RecentRunsTable.vue'
import StatTile, { type StatTileAccent } from '@/components/StatTile.vue'
import WorkloadStateCard from '@/components/WorkloadStateCard.vue'
import { organizationAttention } from '@/util/attention'
import { organizationPath } from '@/util/scope'
import { scanWorkloads } from '@/util/workloads'

const DAY_MS = 24 * 60 * 60 * 1000
/** How far back the runs chart and the recent-runs list look. */
const RUN_WINDOW_DAYS = 7
const RECENT_RUN_COUNT = 5

const props = defineProps<{
  organizationId: string
}>()

const toast = useToast()
const formatEpochDate = DatetimeUtil.formatEpochDate

const basePath = computed(() => organizationPath(props.organizationId))

const organization = ref<Organization | null>(null)
const workloads = ref<Workload[]>([])
const runs = ref<JobRun[]>([])
const applicationCount = ref<number | null>(null)
const projectCount = ref<number | null>(null)
const memberCount = ref<number | null>(null)
const inviteCount = ref<number | null>(null)
const provisioning = ref(false)
const loading = ref(false)
const error = ref<string | null>(null)

const attention = computed(() => organization.value ? organizationAttention(organization.value, workloads.value, runs.value) : [])
const recentRuns = computed(() => runs.value.slice(0, RECENT_RUN_COUNT))

interface Stat {
  label: string
  value: string
  description: string
  tag?: string
  to?: string
  icon?: string
  accent?: StatTileAccent
}

const stats = computed<Stat[]>(() => {
  const running = workloads.value.filter(workload => workload.status === WorkloadStatus.RUNNING).length
  const runningRuns = runs.value.filter(run => run.status === ExecutionStatus.RUNNING).length
  const storage = organization.value?.storage?.status.type ?? null
  let storageDescription: string
  if (storage === DeploymentStatusType.READY) {
    storageDescription = 'deployments can publish'
  } else if (storage === DeploymentStatusType.FAILED) {
    storageDescription = 'deployments are blocked'
  } else {
    storageDescription = 'not ready yet'
  }
  return [
    {
      label: 'Applications',
      value: applicationCount.value?.toString() ?? '—',
      description: `${projectCount.value ?? '—'} project${projectCount.value === 1 ? '' : 's'} across them`,
      to: `${basePath.value}/applications`,
      icon: 'pi-th-large',
      accent: 'sky'
    },
    {
      label: 'Members',
      value: memberCount.value?.toString() ?? '—',
      description: `${inviteCount.value ?? '—'} invitation${inviteCount.value === 1 ? '' : 's'} pending`,
      to: `${basePath.value}/members`,
      icon: 'pi-users',
      accent: 'green'
    },
    {
      label: 'Workloads',
      value: `${running}`,
      description: `running of ${workloads.value.length}`,
      to: `${basePath.value}/workloads`,
      icon: 'pi-box',
      accent: 'amber'
    },
    {
      label: `Jobs · ${RUN_WINDOW_DAYS} d`,
      value: `${runs.value.length}`,
      description: `${runningRuns} running now`,
      to: `${basePath.value}/jobs`,
      icon: 'pi-list-check',
      accent: 'violet'
    },
    {
      label: 'Storage',
      value: storage ?? 'None',
      description: storageDescription,
      tag: storage ? deploymentStatusSeverity(storage) : 'secondary',
      icon: 'pi-cloud',
      accent: storage === DeploymentStatusType.FAILED ? 'red' : 'teal'
    }
  ]
})

async function load() {
  loading.value = true
  error.value = null
  const orgId = props.organizationId
  // A one-row page carries the full count in totalElements
  const firstPage = Pageable.create(0, 1)
  try {
    const [org, apps, projects, members, invites, workloadList, runList] = await Promise.all([
      Kinotic.systemOrganizations.findOrganizationById(orgId),
      Kinotic.systemOrganizations.findApplications(orgId, firstPage),
      Kinotic.systemOrganizations.findProjects(orgId, firstPage),
      Kinotic.systemOrganizations.findMembers(orgId, null, firstPage),
      Kinotic.systemOrganizations.findPendingInvites(orgId, null, firstPage),
      scanWorkloads({ organizationId: orgId }),
      scanJobRuns({ organizationId: orgId, since: Date.now() - RUN_WINDOW_DAYS * DAY_MS })
    ])
    organization.value = org
    applicationCount.value = apps.totalElements ?? 0
    projectCount.value = projects.totalElements ?? 0
    memberCount.value = members.totalElements ?? 0
    inviteCount.value = invites.totalElements ?? 0
    workloads.value = workloadList
    runs.value = runList
  } catch (err) {
    error.value = errorMessage(err, 'Failed to load the organization')
  } finally {
    loading.value = false
  }
}

async function provision() {
  provisioning.value = true
  try {
    organization.value = await Kinotic.systemOrganizations.provisionOrganization(props.organizationId)
    toast.add({ severity: 'success', summary: 'Provisioning started', life: 3000 })
  } catch (err) {
    showErrorToast(toast, 'Failed to start provisioning', err, { life: 8000 })
  } finally {
    provisioning.value = false
  }
}

// The header's organization switcher navigates in place, so the router reuses this
// component instance; refetch when the target organization changes
watch(() => props.organizationId, load, { immediate: true })
</script>
