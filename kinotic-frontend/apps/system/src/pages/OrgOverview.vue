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
      <div class="grid grid-cols-2 gap-4 md:grid-cols-4">
        <StatCard v-for="stat in stats" :key="stat.label" :icon="stat.icon" :tint="stat.tint" :label="stat.label"
                  :value="stat.value" :detail="stat.detail" :to="stat.to" :loading="loading && stat.value === '—'" />
      </div>

      <AttentionList :items="attention" />

      <div class="grid gap-4 lg:grid-cols-2">
        <WorkloadStateCard :workloads="workloads" description="The organization's workloads." :view-all-to="`${basePath}/workloads`" />
        <JobRunsByDayChart :runs="runs" :view-all-to="`${basePath}/jobs`" />
      </div>

      <RecentRunsTable :runs="recentRuns" :scope="{ organizationId }" />

      <DashboardSection :icon="Building2" :tint="TINTS.blue" title="About">
        <div class="px-5 pb-2">
          <FactList :facts="facts" />
        </div>
      </DashboardSection>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, ref, watch, type Component } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { Boxes, Building2, CalendarClock, FileText, Hash, LaptopMinimalCheck, LayoutGrid, Tag as TagIcon, UserRound, Users } from '@lucide/vue'

import { Kinotic, Pageable } from '@kinotic-ai/core'
import { ExecutionStatus, WorkloadStatus, type JobRun, type Organization, type Workload } from '@kinotic-ai/management-api'
import { DashboardSection, DatetimeUtil, FactList, PageHeader, StatCard, TINTS, errorMessage, scanJobRuns } from '@kinotic-ai/frontend-common'

import AttentionList from '@/components/AttentionList.vue'
import JobRunsByDayChart from '@/components/JobRunsByDayChart.vue'
import RecentRunsTable from '@/components/RecentRunsTable.vue'
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

const basePath = computed(() => organizationPath(props.organizationId))

const organization = ref<Organization | null>(null)
const workloads = ref<Workload[]>([])
const runs = ref<JobRun[]>([])
const applicationCount = ref<number | null>(null)
const projectCount = ref<number | null>(null)
const memberCount = ref<number | null>(null)
const inviteCount = ref<number | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const attention = computed(() => organization.value ? organizationAttention(organization.value, workloads.value, runs.value) : [])
const recentRuns = computed(() => runs.value.slice(0, RECENT_RUN_COUNT))

interface Stat {
  label: string
  value: string
  detail: string
  to: string
  icon: Component
  /** One of TINTS. */
  tint: string
}

const stats = computed<Stat[]>(() => {
  const running = workloads.value.filter(workload => workload.status === WorkloadStatus.RUNNING).length
  const runningRuns = runs.value.filter(run => run.status === ExecutionStatus.RUNNING).length
  return [
    {
      label: 'Applications',
      value: applicationCount.value?.toString() ?? '—',
      detail: `${projectCount.value ?? '—'} project${projectCount.value === 1 ? '' : 's'} across them`,
      to: `${basePath.value}/applications`,
      icon: markRaw(LayoutGrid),
      tint: TINTS.blue
    },
    {
      label: 'Members',
      value: memberCount.value?.toString() ?? '—',
      detail: `${inviteCount.value ?? '—'} invitation${inviteCount.value === 1 ? '' : 's'} pending`,
      to: `${basePath.value}/members`,
      icon: markRaw(Users),
      tint: TINTS.green
    },
    {
      label: 'Workloads',
      value: `${running}`,
      detail: `running of ${workloads.value.length}`,
      to: `${basePath.value}/workloads`,
      icon: markRaw(Boxes),
      tint: TINTS.sky
    },
    {
      label: `Jobs · ${RUN_WINDOW_DAYS} d`,
      value: `${runs.value.length}`,
      detail: `${runningRuns} running now`,
      to: `${basePath.value}/jobs`,
      icon: markRaw(LaptopMinimalCheck),
      tint: TINTS.purple
    }
  ]
})

const facts = computed(() => {
  const org = organization.value
  return [
    { label: 'Id', icon: markRaw(Hash), value: props.organizationId, mono: true },
    { label: 'Name', icon: markRaw(TagIcon), value: org?.name ?? '—' },
    { label: 'Description', icon: markRaw(FileText), value: org?.description || '—' },
    { label: 'Created', icon: markRaw(CalendarClock), value: org?.created ? DatetimeUtil.formatRelativeDate(org.created) : '—' },
    { label: 'Created by', icon: markRaw(UserRound), value: org?.createdBy ?? '—', mono: true }
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

// The header's organization switcher navigates in place, so the router reuses this
// component instance; refetch when the target organization changes
watch(() => props.organizationId, load, { immediate: true })
</script>
